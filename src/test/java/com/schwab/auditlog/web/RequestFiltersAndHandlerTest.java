package com.schwab.auditlog.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class RequestFiltersAndHandlerTest {

    @Test
    void requestIdFilter_setsHeaderAndClearsMdc() throws ServletException, IOException {
        RequestIdFilter filter = new RequestIdFilter();

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        // no incoming header -> filter should generate one
        filter.doFilterInternal(req, res, chain);

        String header = res.getHeader(RequestIdFilter.HEADER);
        assertThat(header).isNotBlank();

        // MDC should not contain the key after filter returns
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();

        verify(chain).doFilter(req, res);
    }

    @Test
    void payloadSizeFilter_blocksTooLargePayload() throws ServletException, IOException {
        // set maxBytes to 10 so it's easy to trigger
        PayloadSizeFilter filter = new PayloadSizeFilter(10);

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        req.setContent(new byte[1024]);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(413); // REQUEST_ENTITY_TOO_LARGE
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void payloadSizeFilter_allowsSmallPayload() throws ServletException, IOException {
        PayloadSizeFilter filter = new PayloadSizeFilter(8192);

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        req.setContent(new byte[100]);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain).doFilter(req, res);
    }

    @Test
    void requestLoggingFilter_putsFieldsInMdc_and_cleansUp() throws ServletException, IOException {
        RequestLoggingFilter filter = new RequestLoggingFilter();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/test/path");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> { ((jakarta.servlet.http.HttpServletResponse)response).setStatus(204); };

        filter.doFilterInternal(req, res, chain);

        // After filter completes MDC keys should be removed
        assertThat(MDC.get("method")).isNull();
        assertThat(MDC.get("path")).isNull();
        assertThat(MDC.get("status")).isNull();
        assertThat(MDC.get("durationMs")).isNull();
        assertThat(res.getStatus()).isEqualTo(204);
    }

    @Test
    void exceptionLoggingHandler_returnsRequestIdFromMdc() {
        ExceptionLoggingHandler handler = new ExceptionLoggingHandler();
        MockHttpServletRequest req = new MockHttpServletRequest();

        MDC.put(RequestIdFilter.MDC_KEY, "req-123");
        var resp = handler.handleException(new RuntimeException("boom"), req);

        assertThat(resp.getStatusCodeValue()).isEqualTo(500);
        Map<String, Object> body = resp.getBody();
        assertThat(body).containsEntry("error", "internal_server_error");
        assertThat(body).containsEntry("requestId", "req-123");

        MDC.remove(RequestIdFilter.MDC_KEY);
    }
}
