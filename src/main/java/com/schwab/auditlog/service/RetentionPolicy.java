package com.schwab.auditlog.service;

import java.time.Duration;
import java.time.Instant;

public class RetentionPolicy {

    private final Duration archivalWindow;

    public RetentionPolicy(Duration archivalWindow) {
        this.archivalWindow = archivalWindow;
    }

    public boolean shouldArchive(Instant eventTimestamp, Instant currentTime) {
        return eventTimestamp.isBefore(currentTime.minus(archivalWindow));
    }

    public Instant calculateCutoff(Instant currentTime) {
        return currentTime.minus(archivalWindow);
    }

    public Duration getArchivalWindow() {
        return archivalWindow;
    }
}
