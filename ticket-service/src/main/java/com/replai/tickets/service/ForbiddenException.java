package com.replai.tickets.service;

/** The caller is signed in but their role does not allow the action (HTTP 403). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
