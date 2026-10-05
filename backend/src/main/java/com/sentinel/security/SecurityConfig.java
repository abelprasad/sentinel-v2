package com.sentinel.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * URL authorization for the API.
 *
 * <ul>
 *   <li>{@code /api/health}, {@code /api/public/**} — open. The public demo
 *       must work with zero credentials.</li>
 *   <li>{@code /api/admin/**} — authenticated. HTTP basic for now; Phase 6
 *       replaces it with JWT.</li>
 *   <li>Everything else — denied. Fail closed.</li>
 * </ul>
 *
 * <p>CSRF is disabled because this is a stateless JSON API with no cookie
 * auth; Phase 6 revisits this alongside JWT.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/public/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
