package com.replai.tickets.dto;

import com.replai.tickets.model.SourceRef;
import com.replai.tickets.model.Ticket;

import java.time.Instant;
import java.util.List;

/**
 * Response body for every /api/tickets endpoint. The JPA entity never leaves the service.
 */
public record TicketResponse(
        Long id,
        String customerEmail,
        String subject,
        String message,
        String status,
        String category,
        String priority,
        String sentiment,
        String summary,
        String suggestedReply,
        List<SourceRef> sources,
        Instant createdAt
) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getCustomerEmail(),
                ticket.getSubject(),
                ticket.getMessage(),
                ticket.getStatus().name(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getSentiment(),
                ticket.getSummary(),
                ticket.getSuggestedReply(),
                ticket.getSources(),
                ticket.getCreatedAt()
        );
    }
}
