package com.sentinel.security;

import com.sentinel.config.SentinelProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * URL authorization for the API.
 *
 * <ul>
 *   <li>{@code /api/health}, {@code /api/public/**}, {@code /api/auth/login} — open.
 *       The public demo must work with zero credentials.</li>
 *   <li>{@code GET /api/admin/**} — ANALYST and up (read-only).</li>
 *   <li>Other {@code /api/admin/**} — OPERATOR and up (mutations).</li>
 *   <li>Everything else — denied. Fail closed.</li>
 * </ul>
 *
 * <p>Authentication is JWT Bearer only (see {@link JwtAuthenticationFilter}).
 * There is no password-based login mechanism in the filter chain, no session,
 * and no CSRF surface — this is a stateless JSON API.
 *
 * <p>CORS origins come from the {@code CORS_ALLOWED_ORIGINS} env var
 * (comma-separated). Empty means no cross-origin access.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final SentinelProperties props;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, SentinelProperties props) {
        this.jwtFilter = jwtFilter;
        this.props = props;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            res.getWriter()
                                    .write(
                                            "{\"code\":\"unauthorized\",\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json");
                            res.getWriter()
                                    .write(
                                            "{\"code\":\"forbidden\",\"message\":\"Insufficient permissions\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/public/**", "/api/auth/login")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/**")
                        .hasAnyRole("ANALYST", "OPERATOR", "ADMIN")
                        .requestMatchers("/api/admin/**")
                        .hasAnyRole("OPERATOR", "ADMIN")
                        .anyRequest()
                        .denyAll())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Suppresses Boot's generated-password default user. There is no
     * password-based auth in the filter chain — JWT only — so no
     * UserDetailsService is ever consulted.
     */
    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        String origins = props.security().corsAllowedOrigins();
        if (origins != null && !origins.isBlank()) {
            config.setAllowedOrigins(Arrays.stream(origins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList());
            config.setAllowedMethods(
                    List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        } else {
            config.setAllowedOrigins(List.of());
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
