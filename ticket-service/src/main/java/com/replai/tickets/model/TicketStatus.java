package com.replai.tickets.model;

/**
 * Ticket lifecycle (project.md section 5). Stored as a plain String in the DB.
 *
 * OPEN -> NEEDS_TRIAGE (honest handoff) or ASSIGNED -> IN_PROGRESS -> RESOLVED
 */
public enum TicketStatus {
    OPEN,
    NEEDS_TRIAGE,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED
}
