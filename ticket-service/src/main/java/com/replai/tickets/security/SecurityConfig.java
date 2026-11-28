package com.replai.tickets.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.Map;

/**
 * RBAC rules for ticket-service (project.md section 8):
 *
 *   public    - login, and creating a ticket (the customer form)
 *   any role  - reading tickets, teams, employees and the AI results
 *   LEAD      - assigning from the triage board and every analytics screen
 *   AGENT     - status changes and resolving, but only their own tickets
 *               (enforced in TicketService)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless JWT: no session cookie, no CSRF token to send from the SPA.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/tickets").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/tickets/*/assign").hasAnyRole("LEAD", "ADMIN")
                        .requestMatchers("/api/analytics/**").hasAnyRole("LEAD", "ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(this::writeDenied))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeUnauthorized(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "authentication required - log in first");
    }

    private void writeDenied(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        writeJson(response, HttpServletResponse.SC_FORBIDDEN, "your role does not allow this action");
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", message)));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
