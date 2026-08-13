package com.schwab.auditlog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.schwab.auditlog.dto.CreateAuditEventRequest;
import com.schwab.auditlog.dto.RedactRequest;
import com.schwab.auditlog.service.AuditEventService;
import com.schwab.auditlog.web.ValidationExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditEventControllerNegativePathsTest {

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

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(advice)
                .setValidator(validator)
                .build();
    }

    @Test
    void redact_withEmptyFields_returns400() throws Exception {
        RedactRequest req = new RedactRequest();
        req.setFields(List.of());

        mockMvc.perform(post("/audit/events/1/redact")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEvent_withMissingRequiredFields_returns400() throws Exception {
        CreateAuditEventRequest req = new CreateAuditEventRequest();
        // missing eventType and actorId
        req.setResourceType("ACCOUNT");
        req.setResourceId("ACC-007");
        req.setPayload(Map.of("k","v"));
        req.setTimestamp(Instant.now());

        mockMvc.perform(post("/audit/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
