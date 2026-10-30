package com.bank.model;

import java.util.List;

/**
 * ABSTRACTION: anything that keeps an audit trail of transactions.
 * Includes a DEFAULT METHOD (Java 8+) built with the Streams API.
 */
public interface Auditable {

    void recordAudit(Transaction transaction);

    List<Transaction> getAuditTrail();

    /** Default method: implementers get this behaviour for free. */
    default String auditSummary() {
        List<Transaction> trail = getAuditTrail();
        long ok = trail.stream().filter(t -> t.status() == TransactionStatus.SUCCESS).count();
        return "%d entries (%d successful, %d failed)".formatted(trail.size(), ok, trail.size() - ok);
    }
}
