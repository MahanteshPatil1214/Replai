package com.replai.tickets.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body for PATCH /api/tickets/{id}/assign (used by the triage board).
 */
public record AssignRequest(
        @NotNull(message = "teamId is required")
        Long teamId,
        @NotNull(message = "assigneeId is required")
        Long assigneeId
) {
}
