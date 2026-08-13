package com.schwab.auditlog.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.redisson.api.RateIntervalUnit;

import org.redisson.Redisson;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.api.RateType;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    // Fallback in-memory store for environments without Redis
    private final Map<String, Deque<Long>> requests = new ConcurrentHashMap<>();
    private final int LIMIT = 10; // requests
    private final long WINDOW_MS = 10_000L; // 10 seconds
    
    private final Environment env;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // Redisson client and distributed limiters map
    private volatile RedissonClient redissonClient;
    private final Map<String, RRateLimiter> distributedLimiters = new ConcurrentHashMap<>();

    public RateLimitingFilter(Environment env) {
        this.env = env;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        // Skip for H2 console and skip for actuator if present
        if (path.startsWith("/h2-console")) return true;
        // Disable rate limiting during tests
        for (String p : env.getActiveProfiles()) {
            if (p.equalsIgnoreCase("test") || p.equalsIgnoreCase("securitytest")) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String key = request.getRemoteAddr();

        if (redisTemplate != null) {
            // Prefer Redisson distributed rate limiter (token-bucket semantics)
            try {
                if (redissonClient == null) {
                    synchronized (this) {
                        if (redissonClient == null) {
                            String host = env.getProperty("spring.redis.host", "localhost");
                            String port = env.getProperty("spring.redis.port", "6379");
                            Config config = new Config();
                            config.useSingleServer().setAddress("redis://" + host + ":" + port);
                            redissonClient = Redisson.create(config);
                        }
                    }
                }

                // Create or reuse a distributed rate limiter per key
                RRateLimiter limiter = distributedLimiters.computeIfAbsent(key, k -> {
                    RRateLimiter rl = redissonClient.getRateLimiter("rl:" + k);
                    // set overall rate: LIMIT tokens per WINDOW_MS
                    rl.trySetRate(RateType.OVERALL, LIMIT, WINDOW_MS, RateIntervalUnit.MILLISECONDS);
                    return rl;
                });

                boolean acquired = limiter.tryAcquire(1);
                if (!acquired) {
                    response.setStatus(429);
                    response.getWriter().write("Too Many Requests");
                    return;
                }
            } catch (Exception ex) {
                // If distributed limiter fails, fall back to Lua script approach (best-effort)
                try {
                    String redisKey = "tb:" + key;
                    // capacity tokens, refill rate per millisecond
                    int capacity = LIMIT;
                    double refillPerMs = (double) LIMIT / (double) WINDOW_MS;
                    long now = Instant.now().toEpochMilli();

                    String script = "local key=KEYS[1] \n"
                            + "local capacity=tonumber(ARGV[1]) \n"
                            + "local refillPerMs=tonumber(ARGV[2]) \n"
                            + "local now=tonumber(ARGV[3]) \n"
                            + "local requested=tonumber(ARGV[4]) \n"
                            + "local data=redis.call('HMGET', key, 't', 'ts') \n"
                            + "local tokens=tonumber(data[1]) \n"
                            + "local last=tonumber(data[2]) \n"
                            + "if tokens==false or tokens==nil then tokens=capacity; last=now; end \n"
                            + "local delta=math.max(0, now-last) \n"
                            + "local refill=delta*refillPerMs \n"
                            + "tokens=math.min(capacity, tokens+refill) \n"
                            + "if tokens<requested then \n"
                            + "  redis.call('HMSET', key, 't', tokens, 'ts', now) \n"
                            + "  redis.call('EXPIRE', key, math.ceil(capacity/refillPerMs*2)) \n"
                            + "  return 0 \n"
                            + "else \n"
                            + "  tokens=tokens-requested \n"
                            + "  redis.call('HMSET', key, 't', tokens, 'ts', now) \n"
                            + "  redis.call('EXPIRE', key, math.ceil(capacity/refillPerMs*2)) \n"
                            + "  return 1 \n"
                            + "end\n";

                    org.springframework.data.redis.core.script.RedisScript<Long> redisScript =
                            new org.springframework.data.redis.core.script.DefaultRedisScript<>(script, Long.class);
                    Long allowed = redisTemplate.execute(redisScript, java.util.List.of(redisKey),
                            String.valueOf(capacity), String.valueOf(refillPerMs), String.valueOf(now), "1");

                    if (allowed == null || allowed == 0L) {
                        response.setStatus(429);
                        response.getWriter().write("Too Many Requests");
                        return;
                    }
                } catch (Exception ex2) {
                    // Fall through to in-memory fallback below
                }
            }
        } else {
            // Fallback to in-memory token window (same behavior as before)
            Deque<Long> dq = requests.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (dq) {
                long now = Instant.now().toEpochMilli();
                while (!dq.isEmpty() && now - dq.peekFirst() > WINDOW_MS) dq.removeFirst();
                if (dq.size() >= LIMIT) {
                    response.setStatus(429);
                    response.getWriter().write("Too Many Requests");
                    return;
                }
                dq.addLast(now);
            }
        }

        filterChain.doFilter(request, response);
    }
}
