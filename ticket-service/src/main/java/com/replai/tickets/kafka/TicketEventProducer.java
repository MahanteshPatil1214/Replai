package com.replai.tickets.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes `ticket.created` after a ticket is saved.
 * The message key is the ticketId so all events for one ticket keep their order.
 */
@Component
public class TicketEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TicketEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TicketEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishTicketCreated(TicketCreatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopics.TICKET_CREATED, event.ticketId(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish {} for ticket {}", KafkaTopics.TICKET_CREATED,
                                    event.ticketId(), ex);
                        } else {
                            log.info("Published {} for ticket {} (eventId={})",
                                    KafkaTopics.TICKET_CREATED, event.ticketId(), event.eventId());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Could not serialise ticket.created for ticket {}", event.ticketId(), e);
        }
    }
}
