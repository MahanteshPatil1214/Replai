package com.replai.tickets.controller;

import com.replai.tickets.dto.EmployeeResponse;
import com.replai.tickets.dto.LoginRequest;
import com.replai.tickets.dto.LoginResponse;
import com.replai.tickets.security.CurrentUser;
import com.replai.tickets.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/auth/login and GET /api/auth/me (project.md section 8). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public EmployeeResponse me(@AuthenticationPrincipal CurrentUser user) {
        return authService.currentEmployee(user.id());
    }
}
