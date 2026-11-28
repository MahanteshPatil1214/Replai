package com.replai.tickets.service;

import com.replai.tickets.dto.EmployeeResponse;
import com.replai.tickets.dto.LoginRequest;
import com.replai.tickets.dto.LoginResponse;
import com.replai.tickets.model.Employee;
import com.replai.tickets.repository.EmployeeRepository;
import com.replai.tickets.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns an email + password into a JWT, and loads the employee behind a token. */
@Service
public class AuthService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Employee employee = employeeRepository
                .findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);

        // No hash means the row was created before login existed: same generic error.
        if (employee.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), employee.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return new LoginResponse(jwtService.issue(employee), EmployeeResponse.from(employee));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse currentEmployee(Long employeeId) {
        // The employee behind the token was deleted: the token is no longer valid.
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(InvalidCredentialsException::new);
        return EmployeeResponse.from(employee);
    }
}
