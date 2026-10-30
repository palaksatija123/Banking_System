package com.bank.service;

import com.bank.model.TransactionType;
import com.bank.util.Money;

import java.math.BigDecimal;
import java.util.Objects;

/** RECORD describing a queued (not yet executed) transaction request. */
public record TransactionJob(TransactionType type, String fromAccount, String toAccount, BigDecimal amount) {

    public TransactionJob {
        Objects.requireNonNull(type, "type");
        if (type == TransactionType.INTEREST) {
            throw new IllegalArgumentException("Interest cannot be queued as a job.");
        }
        amount = Money.requirePositive(amount);
    }

    public String describe() {
        return switch (type) {
            case DEPOSIT -> "DEPOSIT " + amount + " to " + toAccount;
            case WITHDRAW -> "WITHDRAW " + amount + " from " + fromAccount;
            case TRANSFER -> "TRANSFER " + amount + " " + fromAccount + " -> " + toAccount;
            case INTEREST -> "INTEREST";
        };
    }
}
