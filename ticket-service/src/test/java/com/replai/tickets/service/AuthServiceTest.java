package com.replai.tickets.service;

import com.replai.tickets.dto.LoginRequest;
import com.replai.tickets.dto.LoginResponse;
import com.replai.tickets.model.Employee;
import com.replai.tickets.model.EmployeeRole;
import com.replai.tickets.repository.EmployeeRepository;
import com.replai.tickets.security.CurrentUser;
import com.replai.tickets.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String SECRET = "replai-local-dev-secret-change-me-0123456789";

    private EmployeeRepository repository;
    private JwtService jwtService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmployeeRepository.class);
        jwtService = new JwtService(SECRET, 60);
        service = new AuthService(repository, new BCryptPasswordEncoder(), jwtService);
    }

    private Employee employeeWithPassword(String rawPassword) {
        Employee employee = mock(Employee.class);
        when(employee.getId()).thenReturn(7L);
        when(employee.getName()).thenReturn("Ravi Sharma");
        when(employee.getEmail()).thenReturn("ravi.sharma@replai.dev");
        when(employee.getRole()).thenReturn(EmployeeRole.AGENT);
        when(employee.getPasswordHash()).thenReturn(new BCryptPasswordEncoder().encode(rawPassword));
        return employee;
    }

    @Test
    void loginReturnsATokenThatCarriesTheEmployeeId() {
        Employee employee = employeeWithPassword("secret");
        when(repository.findByEmail("ravi.sharma@replai.dev")).thenReturn(Optional.of(employee));

        LoginResponse response = service.login(new LoginRequest(" RAVI.Sharma@replai.dev ", "secret"));

        assertThat(response.employee().id()).isEqualTo(7L);
        assertThat(response.employee().role()).isEqualTo("AGENT");
        assertThat(jwtService.parseEmployeeId(response.token())).isEqualTo(7L);
    }

    @Test
    void loginRejectsTheWrongPassword() {
        Employee employee = employeeWithPassword("secret");
        when(repository.findByEmail("ravi.sharma@replai.dev")).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> service.login(new LoginRequest("ravi.sharma@replai.dev", "guess")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsAnUnknownEmail() {
        when(repository.findByEmail("nobody@replai.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@replai.dev", "secret")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void jwtServiceRefusesATokenFromAnotherSecret() {
        String foreign = new JwtService("some-other-secret-that-is-long-enough-12345", 60)
                .issue(employeeWithPassword("secret"));

        assertThat(jwtService.parseEmployeeId(foreign)).isNull();
        assertThat(jwtService.parseEmployeeId("not-a-token")).isNull();
    }

    @Test
    void aSecretShorterThan32BytesIsRejected() {
        assertThatThrownBy(() -> new JwtService("too-short", 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void currentUserKnowsWhenItIsALeadOrAdmin() {
        assertThat(new CurrentUser(1L, "Asha", "a@replai.dev", EmployeeRole.LEAD).isLeadOrAdmin()).isTrue();
        assertThat(new CurrentUser(3L, "Neha", "n@replai.dev", EmployeeRole.ADMIN).isLeadOrAdmin()).isTrue();
        assertThat(new CurrentUser(2L, "Ravi", "r@replai.dev", EmployeeRole.AGENT).isLeadOrAdmin()).isFalse();
    }
}
