package com.bank.exception;

import java.math.BigDecimal;

/**
 * CUSTOM CHECKED EXCEPTION.
 *
 * It extends Exception (not RuntimeException), so the compiler FORCES callers
 * to catch it or declare it with "throws". We use a checked exception here
 * because running out of money is an expected, recoverable business condition
 * that callers must consciously handle.
 */
public class InsufficientBalanceException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String accountNumber;
    private final BigDecimal available;
    private final BigDecimal requested;

    public InsufficientBalanceException(String accountNumber, BigDecimal available, BigDecimal requested) {
        super("Insufficient funds in " + accountNumber + ": available " + available
                + ", requested " + requested);
        this.accountNumber = accountNumber;
        this.available = available;
        this.requested = requested;
    }

    public String getAccountNumber() { return accountNumber; }
    public BigDecimal getAvailable() { return available; }
    public BigDecimal getRequested() { return requested; }

    /** How much more money would have been needed. */
    public BigDecimal getShortfall() { return requested.subtract(available); }
}
