package com.replai.tickets.model;

/**
 * Role inside Replai. Stored as a plain String (project.md schema).
 *
 * AGENT  - works the tickets assigned to them.
 * LEAD   - everything an agent can do, plus triage assignment and analytics.
 * ADMIN  - same permissions as a lead, for the platform owner.
 */
public enum EmployeeRole {
    AGENT,
    LEAD,
    ADMIN
}
