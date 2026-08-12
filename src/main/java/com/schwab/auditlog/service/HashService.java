package com.schwab.auditlog.service;

import com.schwab.auditlog.entity.AuditEvent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class HashService {

    public String calculateHash(AuditEvent event) {
        String canonicalData =
                event.getEventType() + "|" +
                event.getActorId() + "|" +
                event.getResourceType() + "|" +
                event.getResourceId() + "|" +
                event.getPayloadHash() + "|" +
                event.getEventTimestamp() + "|" +
                event.getPreviousHash();

        return sha256(canonicalData);
    }

    public String calculatePayloadHash(String payloadJson) {
        return sha256(payloadJson);
    }

    public String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate hash", exception);
        }
    }
}