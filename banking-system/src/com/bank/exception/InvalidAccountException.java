package com.bank.exception;

/**
 * CUSTOM UNCHECKED EXCEPTION.
 *
 * Extends RuntimeException, so callers are NOT forced to catch it. It signals
 * a programming/input error (bad or unknown account number) rather than a
 * normal business outcome.
 */
public class InvalidAccountException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidAccountException(String message) {
        super(message);
    }
}
