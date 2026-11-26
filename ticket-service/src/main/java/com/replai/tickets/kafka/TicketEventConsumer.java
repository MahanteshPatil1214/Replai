package com.replai.tickets.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.replai.tickets.service.TicketEnrichmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes `ticket.enriched` and updates the ticket.
 *
 * A malformed or unexpected message is logged and skipped - the listener never
 * throws, so one bad record cannot stop the consumer.
 */
@Component
public class TicketEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final TicketEnrichmentService enrichmentService;

    public TicketEventConsumer(ObjectMapper objectMapper, TicketEnrichmentService enrichmentService) {
        this.objectMapper = objectMapper;
        this.enrichmentService = enrichmentService;
    }

    @KafkaListener(topics = KafkaTopics.TICKET_ENRICHED, groupId = "${spring.kafka.consumer.group-id}")
    public void onTicketEnriched(String payload) {
        try {
            TicketEnrichedEvent event = objectMapper.readValue(payload, TicketEnrichedEvent.class);
            enrichmentService.apply(event);
        } catch (Exception e) { // one bad message must never stop the consumer
            log.error("Skipping unreadable {} message: {}", KafkaTopics.TICKET_ENRICHED, e.getMessage());
        }
    }
}
