package com.schwab.auditlog.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PayloadSizeFilter extends HttpFilter {

    private final long maxBytes;

    public PayloadSizeFilter(@Value("${audit.payload.max-bytes:8192}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        long contentLength = req.getContentLengthLong();
        if (contentLength > 0 && contentLength > maxBytes) {
            res.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Payload too large");
            return;
        }
        chain.doFilter(req, res);
    }
}
