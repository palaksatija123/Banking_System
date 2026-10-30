package com.bank.model;

import java.math.BigDecimal;

/**
 * ENUM WITH BEHAVIOUR: acts as a small factory. Adding a new account type
 * means adding a constant and one case here.
 */
public enum AccountType {
    SAVINGS,
    CHECKING;

    public Account create(String accountNumber, String ownerName, BigDecimal openingBalance) {
        // Java 14+ switch expression: exhaustive over the enum, no fall-through.
        return switch (this) {
            case SAVINGS -> new SavingsAccount(accountNumber, ownerName, openingBalance);
            case CHECKING -> new CheckingAccount(accountNumber, ownerName, openingBalance);
        };
    }
}
