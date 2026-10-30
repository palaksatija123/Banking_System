package com.bank.model;

/** ENUM with a field and constructor: a fixed set of constants that carry data. */
public enum TransactionType {
    DEPOSIT("Deposit"),
    WITHDRAW("Withdrawal"),
    TRANSFER("Transfer"),
    INTEREST("Interest");

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
