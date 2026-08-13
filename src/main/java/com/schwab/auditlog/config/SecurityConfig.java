package com.schwab.auditlog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.schwab.auditlog.security.RateLimitingFilter;

@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .headers().frameOptions().sameOrigin()
                .and()
                .cors().and()
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").hasRole("ADMIN")
                        .requestMatchers("POST", "/audit/events").hasAnyRole("WRITER","ADMIN")
                        .requestMatchers("POST", "/audit/events/*/redact").hasRole("ADMIN")
                        .requestMatchers("POST", "/audit/events/*/archive").hasRole("ADMIN")
                        .requestMatchers("POST", "/audit/retention/archive").hasRole("ADMIN")
                        .requestMatchers("GET", "/audit/verify").hasAnyRole("AUDITOR","ADMIN")
                        .requestMatchers("GET", "/audit/exports/**").hasAnyRole("AUDITOR","ADMIN")
                        .requestMatchers("GET", "/audit/events").hasAnyRole("AUDITOR","WRITER","ADMIN")
                        .anyRequest().authenticated()
                )
                .httpBasic();

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(RateLimitingFilter filter) {
        FilterRegistrationBean<RateLimitingFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        reg.addUrlPatterns("/audit/*");
        reg.setOrder(1);
        return reg;
    }

    @Bean
    public UserDetailsService users() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        manager.createUser(User.withUsername("admin").password(encoder.encode("adminPass")).roles("ADMIN","WRITER","AUDITOR").build());
        manager.createUser(User.withUsername("writer").password(encoder.encode("writerPass")).roles("WRITER").build());
        manager.createUser(User.withUsername("auditor").password(encoder.encode("auditorPass")).roles("AUDITOR").build());
        return manager;
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOrigins(java.util.List.of("http://localhost:8080"));
        configuration.setAllowedMethods(java.util.List.of("GET","POST","PUT","DELETE","OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Authorization","Cache-Control","Content-Type"));
        configuration.setAllowCredentials(true);
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
