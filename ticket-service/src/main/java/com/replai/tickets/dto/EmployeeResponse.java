package com.replai.tickets.dto;

import com.replai.tickets.model.Employee;

/**
 * Response body for GET /api/employees.
 */
public record EmployeeResponse(Long id, String name, String email, Long teamId, String role) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getName(),
                employee.getEmail(),
                employee.getTeamId(),
                employee.getRole().name());
    }
}
