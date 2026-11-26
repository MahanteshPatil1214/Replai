package com.replai.tickets.model;

/**
 * Ticket lifecycle. Stored as a plain String in the DB (project.md schema).
 */
public enum TicketStatus {
    OPEN,
    ENRICHED,
    RESOLVED
}
