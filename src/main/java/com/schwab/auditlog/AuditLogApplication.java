package com.schwab.auditlog;

import com.schwab.auditlog.service.RetentionPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

@SpringBootApplication
@EnableScheduling
public class AuditLogApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditLogApplication.class, args);
    }

    @Bean
    public RetentionPolicy retentionPolicy(@Value("${audit.retention.days:30}") long archivalDays) {
        return new RetentionPolicy(Duration.ofDays(archivalDays));
    }
}
