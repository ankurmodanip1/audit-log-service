package com.schwab.auditlog.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class ExceptionLoggingHandler {
    private static final Logger log = LoggerFactory.getLogger(ExceptionLoggingHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex, HttpServletRequest request) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        log.error("Unhandled exception, requestId={}", requestId, ex);
        Map<String, Object> body = new HashMap<>();
        body.put("error", "internal_server_error");
        body.put("requestId", requestId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
