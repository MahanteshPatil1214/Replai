package com.replai.tickets.service;

/** Login failed: unknown email or wrong password (HTTP 401). */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("invalid email or password");
    }
}
