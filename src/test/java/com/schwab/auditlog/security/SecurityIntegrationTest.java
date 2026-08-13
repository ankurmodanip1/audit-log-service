package com.schwab.auditlog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.auditlog.dto.CreateAuditEventRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.time.Instant;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("securitytest")
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void writer_can_create_event() throws Exception {
        CreateAuditEventRequest request = new CreateAuditEventRequest();
        request.setEventType("USER_LOGIN");
        request.setActorId("user-201");
        request.setResourceType("ACCOUNT");
        request.setResourceId("ACC-201");
        request.setPayload(Map.of("ipAddress","10.10.10.10","status","SUCCESS"));
        request.setTimestamp(Instant.parse("2026-08-12T10:00:00Z"));

        mockMvc.perform(post("/audit/events")
                .with(httpBasic("writer","writerPass"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void auditor_cannot_create_event() throws Exception {
        CreateAuditEventRequest request = new CreateAuditEventRequest();
        request.setEventType("USER_LOGIN");
        request.setActorId("user-201");
        request.setResourceType("ACCOUNT");
        request.setResourceId("ACC-201");
        request.setPayload(Map.of("ipAddress","10.10.10.10","status","SUCCESS"));
        request.setTimestamp(Instant.parse("2026-08-12T10:00:00Z"));

        mockMvc.perform(post("/audit/events")
                .with(httpBasic("auditor","auditorPass"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditor_can_verify_chain() throws Exception {
        mockMvc.perform(get("/audit/verify")
                .with(httpBasic("auditor","auditorPass")))
                .andExpect(status().isOk());
    }
}
