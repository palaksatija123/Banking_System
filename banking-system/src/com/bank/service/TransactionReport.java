package com.bank.service;

import com.bank.model.Transaction;
import com.bank.model.TransactionStatus;
import com.bank.model.TransactionType;
import com.bank.util.GenericUtils;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * STREAMS + LAMBDAS.
 *
 * Every method is a declarative pipeline: source -> filter -> sort -> collect.
 * Predicate<Transaction> and Comparator<Transaction> are functional interfaces,
 * so callers pass behaviour as a lambda, e.g. t -> t.amount().signum() > 0.
 */
public final class TransactionReport {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Ready-made comparators (lambdas / method references). */
    public static final Comparator<Transaction> NEWEST_FIRST =
            Comparator.comparing(Transaction::timestamp).thenComparing(Transaction::id).reversed();
    public static final Comparator<Transaction> OLDEST_FIRST =
            Comparator.comparing(Transaction::timestamp).thenComparing(Transaction::id);
    public static final Comparator<Transaction> LARGEST_FIRST =
            Comparator.comparing(Transaction::amount).reversed();

    private TransactionReport() { }

    /** The general-purpose pipeline: filter by any predicate, sort by any comparator. */
    public static List<Transaction> filter(Collection<Transaction> all,
                                           Predicate<Transaction> condition,
                                           Comparator<Transaction> ordering) {
        return all.stream()
                .filter(condition)
                .sorted(ordering)
                .collect(Collectors.toList());
    }

    public static List<Transaction> forAccount(Collection<Transaction> all, String accountNumber, Comparator<Transaction> ordering) {
        return filter(all, t -> t.involves(accountNumber), ordering);
    }

    public static List<Transaction> ofType(Collection<Transaction> all, TransactionType type, Comparator<Transaction> ordering) {
        return filter(all, t -> t.type() == type, ordering);
    }

    public static List<Transaction> atLeast(Collection<Transaction> all, BigDecimal min, Comparator<Transaction> ordering) {
        return filter(all, t -> t.amount().compareTo(min) >= 0, ordering);
    }

    public static List<Transaction> failed(Collection<Transaction> all, Comparator<Transaction> ordering) {
        return filter(all, t -> t.status() == TransactionStatus.FAILED, ordering);
    }

    public static List<Transaction> largest(Collection<Transaction> all, int n) {
        // Uses our own generic helper from GenericUtils.
        return GenericUtils.topN(all, LARGEST_FIRST, n);
    }

    /** Sum of successful amounts per transaction type (collector chain). */
    public static Map<TransactionType, BigDecimal> totalsByType(Collection<Transaction> all) {
        return all.stream()
                .filter(t -> t.status() == TransactionStatus.SUCCESS)
                .collect(Collectors.groupingBy(
                        Transaction::type,
                        () -> new EnumMap<TransactionType, BigDecimal>(TransactionType.class),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::amount, BigDecimal::add)));
    }

    public static Map<TransactionStatus, Long> countByStatus(Collection<Transaction> all) {
        return all.stream()
                .collect(Collectors.groupingBy(Transaction::status, Collectors.counting()));
    }

    // ------------------------------------------------------------------ output

    public static String format(Transaction t) {
        return "%-11s %-19s %-10s %-8s %-8s %12s  %-7s %s".formatted(
                t.id(), TIME.format(t.timestamp()), t.type(), t.fromAccount(), t.toAccount(),
                t.amount(), t.status(), t.note());
    }

    public static void print(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            System.out.println("  (no transactions)");
            return;
        }
        System.out.printf("%-11s %-19s %-10s %-8s %-8s %12s  %-7s %s%n",
                "ID", "TIME", "TYPE", "FROM", "TO", "AMOUNT", "STATUS", "NOTE");
        transactions.forEach(t -> System.out.println(format(t)));   // method reference-style forEach
        System.out.println(transactions.size() + " transaction(s).");
    }
}
