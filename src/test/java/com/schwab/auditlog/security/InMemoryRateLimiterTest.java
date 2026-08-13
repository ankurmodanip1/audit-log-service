package com.schwab.auditlog.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.core.env.Environment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import jakarta.servlet.ServletException;

class InMemoryRateLimiterTest {

    @Test
    void allowsRequestsUnderLimitAndBlocksWhenExceeded() throws ServletException, IOException {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[0]);
        RateLimitingFilter filter = new RateLimitingFilter(env);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // send LIMIT requests
        int limit = 10;
        for (int i = 0; i < limit; i++) {
            MockFilterChain chain = new MockFilterChain();
            res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isNotEqualTo(429);
        }

        // Next request should be blocked
        MockFilterChain finalChain = new MockFilterChain();
        res = new MockHttpServletResponse();
        filter.doFilter(req, res, finalChain);
        assertThat(res.getStatus()).isEqualTo(429);
    }
}
