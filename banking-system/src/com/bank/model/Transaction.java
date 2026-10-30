package com.bank.model;

import com.bank.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * JAVA RECORD: an immutable data carrier (Java 16+).
 *
 * The compiler generates the constructor, accessors (id(), type(), ...),
 * equals(), hashCode() and toString(). All fields are final, so a Transaction
 * can be shared freely between threads without any locking.
 *
 * The "compact constructor" below runs before the fields are assigned and is
 * the place for validation / normalisation.
 */
public record Transaction(String id,
                          TransactionType type,
                          String fromAccount,
                          String toAccount,
                          BigDecimal amount,
                          LocalDateTime timestamp,
                          TransactionStatus status,
                          String note) {

    public static final String NONE = "-";
    public static final String CSV_HEADER = "id,type,from,to,amount,timestamp,status,note";
    private static final int CSV_FIELDS = 8;

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(status, "status");
        fromAccount = (fromAccount == null || fromAccount.isBlank()) ? NONE : fromAccount;
        toAccount = (toAccount == null || toAccount.isBlank()) ? NONE : toAccount;
        amount = Money.requirePositive(amount);
        timestamp = timestamp.truncatedTo(ChronoUnit.MILLIS);
        // Keep the CSV format safe: no commas or line breaks inside the note.
        note = note == null ? "" : note.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }

    /** True if the given account is the source or the destination. */
    public boolean involves(String accountNumber) {
        return fromAccount.equals(accountNumber) || toAccount.equals(accountNumber);
    }

    /** Serialises to one CSV line (matches CSV_HEADER). */
    public String toCsv() {
        return String.join(",", id, type.name(), fromAccount, toAccount,
                amount.toPlainString(), timestamp.toString(), status.name(), note);
    }

    /** Parses one CSV line. Throws IllegalArgumentException for corrupt lines. */
    public static Transaction fromCsv(String line) {
        String[] p = line.split(",", -1);
        if (p.length != CSV_FIELDS) {
            throw new IllegalArgumentException("Expected " + CSV_FIELDS + " fields but found " + p.length);
        }
        return new Transaction(p[0], TransactionType.valueOf(p[1]), p[2], p[3],
                Money.parse(p[4]), LocalDateTime.parse(p[5]), TransactionStatus.valueOf(p[6]), p[7]);
    }
}
