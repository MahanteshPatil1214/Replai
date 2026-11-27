package com.replai.tickets.service;

import com.replai.tickets.kafka.TicketEnrichedEvent;
import com.replai.tickets.model.Ticket;
import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies `ticket.enriched` to a ticket.
 *
 * The write is idempotent: replaying the same event simply re-sets the same
 * columns, so duplicate deliveries are harmless (see agent.md).
 */
@Service
public class TicketEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(TicketEnrichmentService.class);

    private final TicketRepository ticketRepository;

    public TicketEnrichmentService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public boolean apply(TicketEnrichedEvent event) {
        Long ticketId = parseTicketId(event);
        if (ticketId == null) {
            return false;
        }

        return ticketRepository.findById(ticketId)
                .map(ticket -> applyTo(ticket, event))
                .orElseGet(() -> {
                    log.warn("Ignoring enrichment for unknown ticket {}", ticketId);
                    return false;
                });
    }

    private boolean applyTo(Ticket ticket, TicketEnrichedEvent event) {
        ticket.setCategory(event.category());
        ticket.setPriority(event.priority());
        ticket.setSentiment(event.sentiment());
        ticket.setSummary(event.summary());
        ticket.setSuggestedReply(event.suggestedReply());
        ticket.setConfidence(event.confidence());
        ticket.setNeedsHuman(event.needsHuman());
        ticket.setKnowledgeGap(event.knowledgeGap());
        ticket.setSources(event.sources());
        ticket.setStatus(TicketStatus.ENRICHED);

        ticketRepository.save(ticket);
        log.info("Ticket {} enriched (eventId={}, {} sources)",
                ticket.getId(), event.eventId(),
                event.sources() == null ? 0 : event.sources().size());
        return true;
    }

    private Long parseTicketId(TicketEnrichedEvent event) {
        try {
            return Long.valueOf(event.ticketId());
        } catch (NumberFormatException | NullPointerException e) {
            log.error("Ignoring enrichment with unusable ticketId '{}'", event.ticketId());
            return null;
        }
    }
}
