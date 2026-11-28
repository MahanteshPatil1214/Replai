package com.replai.tickets.security;

import com.replai.tickets.model.Employee;
import com.replai.tickets.repository.EmployeeRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads "Authorization: Bearer <token>" and, when the token is valid,
 * puts the employee into the SecurityContext for the rest of the request.
 * Invalid or missing tokens simply mean "not authenticated" - the security
 * rules in SecurityConfig then answer 401.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final EmployeeRepository employeeRepository;

    public JwtAuthenticationFilter(JwtService jwtService, EmployeeRepository employeeRepository) {
        this.jwtService = jwtService;
        this.employeeRepository = employeeRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long employeeId = employeeIdFrom(request.getHeader("Authorization"));

        if (employeeId != null) {
            Optional<Employee> employee = employeeRepository.findById(employeeId);
            if (employee.isPresent()) {
                CurrentUser user = new CurrentUser(
                        employee.get().getId(),
                        employee.get().getName(),
                        employee.get().getEmail(),
                        employee.get().getRole());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Long employeeIdFrom(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return jwtService.parseEmployeeId(header.substring("Bearer ".length()).trim());
    }
}
