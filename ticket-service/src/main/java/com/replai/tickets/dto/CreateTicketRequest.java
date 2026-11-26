package com.replai.tickets.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Body for POST /api/tickets.
 */
public record CreateTicketRequest(
        @NotBlank(message = "customerEmail is required")
        @Email(message = "customerEmail must be a valid email")
        String customerEmail,

        @NotBlank(message = "subject is required")
        String subject,

        @NotBlank(message = "message is required")
        String message
) {
}
