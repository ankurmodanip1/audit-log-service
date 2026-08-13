package com.schwab.auditlog.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("redis-integration")
public class RedisRateLimiterIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0.11").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        String envHost = System.getenv("REDIS_HOST");
        String envPort = System.getenv("REDIS_PORT");
        if (envHost != null && envPort != null) {
            registry.add("spring.redis.host", () -> envHost);
            registry.add("spring.redis.port", () -> Integer.parseInt(envPort));
        } else {
            registry.add("spring.redis.host", redis::getHost);
            registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void rateLimitEnforcedByRedis() throws Exception {
        TestRestTemplate client = restTemplate.withBasicAuth("writer", "writerPass");
        String url = "http://localhost:" + port + "/audit/events";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("eventType", "USER_LOGIN");
        body.put("actorId", "user-101");
        body.put("resourceType", "ACCOUNT");
        body.put("resourceId", "ACC-001");
        body.put("timestamp", "2026-08-11T10:00:00Z");
        Map<String,Object> payload = new HashMap<>();
        payload.put("ipAddress","10.10.10.10");
        payload.put("status","SUCCESS");
        body.put("payload", payload);

        HttpEntity<Map<String,Object>> entity = new HttpEntity<>(body, headers);

        int limit = 10;
        // send limit + 2 requests
        for (int i = 1; i <= limit + 2; i++) {
            ResponseEntity<String> resp = client.postForEntity(url, entity, String.class);
            if (i <= limit) {
                assertEquals(HttpStatus.OK, resp.getStatusCode(), "request " + i + " should succeed");
            } else {
                assertEquals(HttpStatus.TOO_MANY_REQUESTS, resp.getStatusCode(), "request " + i + " should be rate limited");
            }
        }
    }
}
