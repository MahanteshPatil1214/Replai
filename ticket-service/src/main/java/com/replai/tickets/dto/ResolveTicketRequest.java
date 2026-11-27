package com.replai.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for POST /api/tickets/{id}/resolve: what the agent finally sent, and
 * what they did with the AI draft (ACCEPTED, EDITED or REJECTED).
 */
public record ResolveTicketRequest(
        @NotBlank(message = "finalReply is required")
        @Size(max = 20000, message = "finalReply is too long")
        String finalReply,
        @NotBlank(message = "draftAction is required")
        @Pattern(regexp = "ACCEPTED|EDITED|REJECTED",
                message = "draftAction must be ACCEPTED, EDITED or REJECTED")
        String draftAction,
        Long resolvedBy
) {
}
