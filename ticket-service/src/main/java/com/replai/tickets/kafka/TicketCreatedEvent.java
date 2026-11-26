package com.replai.tickets.kafka;

import java.time.Instant;

/**
 * Event contract for `ticket.created` (see contracts/ticket.created.json).
 * Field names must match the Python side exactly.
 */
public record TicketCreatedEvent(
        String eventId,
        String ticketId,
        String customerEmail,
        String subject,
        String message,
        Instant createdAt
) {
}
