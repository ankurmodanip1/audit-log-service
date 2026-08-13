package com.schwab.auditlog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.schwab.auditlog.dto.CreateAuditEventRequest;
import com.schwab.auditlog.service.AuditEventService;
import com.schwab.auditlog.web.PayloadSizeFilter;
import com.schwab.auditlog.web.ValidationExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditEventControllerValidationTest {

    private AuditEventController controller;
    private AuditEventService service;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static class AuditEventServiceStub extends AuditEventService {
        AuditEventServiceStub() { super(null, null, null, null); }
    }

    @BeforeEach
    void setUp() {
        service = new AuditEventServiceStub();
        controller = new AuditEventController(service);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ValidationExceptionHandler advice = new ValidationExceptionHandler();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        // Use a small max to easily trigger the size filter in test
        PayloadSizeFilter smallFilter = new PayloadSizeFilter(16);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(advice)
                .setValidator(validator)
                .addFilters(smallFilter)
                .build();
    }

    @Test
    void whenContentLengthExceedsLimit_thenReturns413() throws Exception {
        CreateAuditEventRequest req = new CreateAuditEventRequest();
        req.setEventType("USER_LOGIN");
        req.setActorId("user-101");
        req.setResourceType("ACCOUNT");
        req.setResourceId("ACC-001");
        req.setPayload(Map.of("ipAddress", "10.10.10.10"));
        req.setTimestamp(Instant.parse("2026-08-12T10:00:00Z"));

        String body = objectMapper.writeValueAsString(req);

        // set Content-Length header larger than the filter's max (16)
        mockMvc.perform(post("/audit/events")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Content-Length", String.valueOf(body.getBytes().length + 100))
                .content(body))
                .andExpect(status().isRequestEntityTooLarge());
    }

    @Test
    void whenPayloadMapExceedsDtoSize_thenReturns400() throws Exception {
        CreateAuditEventRequest req = new CreateAuditEventRequest();
        req.setEventType("USER_LOGIN");
        req.setActorId("user-101");
        req.setResourceType("ACCOUNT");
        req.setResourceId("ACC-001");

        // create payload with more than 2048 entries to violate @Size(max=2048)
        Map<String, Object> big = new HashMap<>();
        for (int i = 0; i < 2050; i++) {
            big.put("k" + i, "v" + i);
        }
        req.setPayload(big);
        req.setTimestamp(Instant.parse("2026-08-12T10:00:00Z"));

        String body = objectMapper.writeValueAsString(req);

        // execute without the size filter so the DTO validation is reached
        MockMvc mockMvcNoFilter = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ValidationExceptionHandler())
            .setValidator(new LocalValidatorFactoryBean())
            .build();

        mockMvcNoFilter.perform(post("/audit/events")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isBadRequest());
    }
}
