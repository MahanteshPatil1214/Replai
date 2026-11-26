package com.replai.tickets.kafka;

/**
 * The only two topics in the system (see project.md).
 */
public final class KafkaTopics {

    public static final String TICKET_CREATED = "ticket.created";
    public static final String TICKET_ENRICHED = "ticket.enriched";

    private KafkaTopics() {
    }
}
