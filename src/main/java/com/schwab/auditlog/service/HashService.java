package com.schwab.auditlog.service;

import com.schwab.auditlog.entity.AuditEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class HashService {

    private final ObjectMapper canonicalMapper;

    public HashService() {
        this.canonicalMapper = new ObjectMapper();
        // Ensure map entries are ordered by key for deterministic output
        this.canonicalMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String calculateHash(AuditEvent event) {
        String canonicalData =
                safe(event.getEventType()) + "|" +
                safe(event.getActorId()) + "|" +
                safe(event.getResourceType()) + "|" +
                safe(event.getResourceId()) + "|" +
                safe(event.getPayloadHash()) + "|" +
                safe(String.valueOf(event.getEventTimestamp())) + "|" +
                safe(event.getPreviousHash());

        return sha256(canonicalData);
    }

    public String calculatePayloadHash(String payloadJson) {
        if (payloadJson == null) return sha256("");
        try {
            JsonNode node = canonicalMapper.readTree(payloadJson);
            JsonNode sorted = sortJson(node);
            String canonical = canonicalMapper.writeValueAsString(sorted);
            return sha256(canonical);
        } catch (Exception e) {
            // If payload isn't valid JSON, fallback to hashing raw string
            return sha256(payloadJson);
        }
    }

    private JsonNode sortJson(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) return node;
        if (node.isObject()) {
            // create a new ObjectNode with sorted field names
            com.fasterxml.jackson.databind.node.ObjectNode obj = canonicalMapper.createObjectNode();
            java.util.List<String> names = new java.util.ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            java.util.Collections.sort(names);
            for (String name : names) {
                obj.set(name, sortJson(node.get(name)));
            }
            return obj;
        }
        if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode arr = canonicalMapper.createArrayNode();
            for (JsonNode el : node) {
                arr.add(sortJson(el));
            }
            return arr;
        }
        return node;
    }

    private String safe(String s) {
        return s == null ? "" : s;
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