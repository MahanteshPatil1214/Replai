package com.replai.tickets.dto;

/** Leaderboard row: who solved the most tickets, and how fast. */
public record EmployeeAnalyticsResponse(
        Long employeeId,
        String name,
        String team,
        long resolved,
        Double avgResolutionHours
) {
}
