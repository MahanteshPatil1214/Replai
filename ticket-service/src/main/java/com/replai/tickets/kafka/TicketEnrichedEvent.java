package com.replai.tickets.kafka;

import com.replai.tickets.model.SourceRef;

import java.util.List;

/**
 * Event contract for `ticket.enriched` (see contracts/ticket.enriched.json).
 */
public record TicketEnrichedEvent(
        String eventId,
        String ticketId,
        String category,
        String priority,
        String sentiment,
        String summary,
        String suggestedReply,
        Double confidence,
        Boolean needsHuman,
        Boolean knowledgeGap,
        List<SourceRef> sources
) {
}
