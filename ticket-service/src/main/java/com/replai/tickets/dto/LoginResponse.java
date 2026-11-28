package com.replai.tickets.dto;

/**
 * Body for POST /api/auth/login. The token goes in the
 * "Authorization: Bearer <token>" header of every later call.
 */
public record LoginResponse(String token, EmployeeResponse employee) {
}
