package com.schwab.auditlog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.auditlog.dto.CreateAuditEventRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("securitytest")
public class NegativeSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unauthenticated_post_returns_401() throws Exception {
        CreateAuditEventRequest request = new CreateAuditEventRequest();
        request.setEventType("USER_LOGIN");
        request.setActorId("user-300");
        request.setResourceType("ACCOUNT");
        request.setResourceId("ACC-300");
        request.setPayload(Map.of("ipAddress","10.10.10.10","status","SUCCESS"));
        request.setTimestamp(Instant.parse("2026-08-12T10:00:00Z"));

        mockMvc.perform(post("/audit/events")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalid_credentials_returns_401() throws Exception {
        CreateAuditEventRequest request = new CreateAuditEventRequest();
        request.setEventType("USER_LOGIN");
        request.setActorId("user-301");
        request.setResourceType("ACCOUNT");
        request.setResourceId("ACC-301");
        request.setPayload(Map.of("ipAddress","10.10.10.10","status","SUCCESS"));
        request.setTimestamp(Instant.parse("2026-08-12T10:00:00Z"));

        mockMvc.perform(post("/audit/events")
            .with(httpBasic("bad","bad"))
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void missing_csrf_for_writer_returns_403() throws Exception {
        CreateAuditEventRequest request = new CreateAuditEventRequest();
        request.setEventType("USER_LOGIN");
        request.setActorId("user-302");
        request.setResourceType("ACCOUNT");
        request.setResourceId("ACC-302");
        request.setPayload(Map.of("ipAddress","10.10.10.10","status","SUCCESS"));
        request.setTimestamp(Instant.parse("2026-08-12T10:00:00Z"));

        mockMvc.perform(post("/audit/events")
                .with(httpBasic("writer","writerPass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_verify_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/audit/verify"))
                .andExpect(status().isUnauthorized());
    }
}
