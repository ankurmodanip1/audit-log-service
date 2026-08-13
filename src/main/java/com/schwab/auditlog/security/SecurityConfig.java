package com.schwab.auditlog.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@Profile("securitytest")
public class SecurityConfig {
 
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("POST", "/audit/events").hasRole("WRITER")
                .requestMatchers("POST", "/audit/events/*/redact").hasRole("ADMIN")
                .requestMatchers("POST", "/audit/events/*/archive").hasRole("ADMIN")
                .requestMatchers("POST", "/audit/retention/**").hasRole("ADMIN")
                .requestMatchers("GET", "/audit/verify").hasRole("AUDITOR")
                .requestMatchers("GET", "/audit/exports/**").hasRole("AUDITOR")
                .requestMatchers("GET", "/audit/events").hasRole("AUDITOR")
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());

        // allow H2 console frames (tests may hit H2 console)
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    // Test profile users (writer/auditor/admin) used by integration tests
    @Bean
    @Profile("securitytest")
    public UserDetailsService usersForTests(
            @Value("${test.users.writer:writer}") String writer,
            @Value("${test.users.writer.pass:writerPass}") String writerPass,
            @Value("${test.users.auditor:auditor}") String auditor,
            @Value("${test.users.auditor.pass:auditorPass}") String auditorPass,
            @Value("${test.users.admin:admin}") String admin,
            @Value("${test.users.admin.pass:adminPass}") String adminPass
    ) {
        InMemoryUserDetailsManager mgr = new InMemoryUserDetailsManager();
        mgr.createUser(User.withDefaultPasswordEncoder().username(writer).password(writerPass).roles("WRITER").build());
        mgr.createUser(User.withDefaultPasswordEncoder().username(auditor).password(auditorPass).roles("AUDITOR").build());
        mgr.createUser(User.withDefaultPasswordEncoder().username(admin).password(adminPass).roles("ADMIN").build());
        return mgr;
    }
}
