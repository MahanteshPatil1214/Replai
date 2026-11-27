package com.replai.tickets.service;

/**
 * Raised when the triage board sends a team/employee combination that does not
 * exist (an employee must belong to the chosen team).
 */
public class InvalidAssignmentException extends RuntimeException {

    public InvalidAssignmentException(Long teamId, Long employeeId) {
        super("employee " + employeeId + " is not part of team " + teamId);
    }
}
