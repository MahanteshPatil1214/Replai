package com.replai.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body for PATCH /api/tickets/{id}/status.
 */
public record UpdateStatusRequest(
        @NotBlank(message = "status is required")
        @Pattern(regexp = "OPEN|ENRICHED|RESOLVED", message = "status must be OPEN, ENRICHED or RESOLVED")
        String status
) {
}
