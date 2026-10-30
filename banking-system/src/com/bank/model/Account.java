package com.bank.model;

import com.bank.exception.InsufficientBalanceException;
import com.bank.exception.InvalidAccountException;
import com.bank.util.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * ABSTRACT BASE CLASS for all accounts.
 *
 * OOP topics shown here
 * ---------------------
 * - Encapsulation : every field is private. There is deliberately NO
 *                   setBalance(): the balance can only change through
 *                   deposit()/withdraw()/transfer(), which validate the change.
 * - Abstraction   : implements the Transactable and Auditable interfaces.
 * - Inheritance / Polymorphism : subclasses supply the account-specific rules
 *                   (interest, withdrawal limit, overdraft) by overriding the
 *                   abstract methods. withdraw() is a TEMPLATE METHOD: the
 *                   algorithm is fixed here, the variable parts are abstract.
 *
 * Concurrency
 * -----------
 * The balance is guarded by a ReentrantLock. "Reentrant" means the thread that
 * already holds the lock can acquire it again, which lets transfer() lock two
 * accounts and then call deposit()/withdraw() safely.
 */
public abstract class Account implements Transactable, Auditable {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("ACC\\d{4,10}");
    private static final Pattern NAME_PATTERN = Pattern.compile("[\\p{L} .'-]{2,50}");

    private final String accountNumber;          // immutable identity
    private volatile String ownerName;           // volatile: visible across threads
    private BigDecimal balance;                  // guarded by 'lock'
    private final ReentrantLock lock = new ReentrantLock();
    private final List<Transaction> auditTrail = new ArrayList<>(); // guarded by itself

    /**
     * @param openingBalance normally zero; may be negative only when restoring an
     *                       overdrawn checking account from the ledger file.
     */
    protected Account(String accountNumber, String ownerName, BigDecimal openingBalance) {
        if (accountNumber == null || !NUMBER_PATTERN.matcher(accountNumber).matches()) {
            throw new InvalidAccountException("Invalid account number: " + accountNumber);
        }
        this.accountNumber = accountNumber;
        setOwnerName(ownerName);
        this.balance = Money.normalize(Objects.requireNonNull(openingBalance, "openingBalance"));
    }

    // ------------------------------------------------------------------
    // Polymorphic hooks - each subclass defines its own rules
    // ------------------------------------------------------------------

    public abstract AccountType getType();

    /** Interest for one month (negative = a charge, e.g. overdraft interest). */
    protected abstract BigDecimal calculateMonthlyInterest();

    /** Largest amount allowed in a single withdrawal. */
    public abstract BigDecimal getMaxWithdrawalPerTransaction();

    /** How far below zero the balance may go (0 = no overdraft). */
    public abstract BigDecimal getOverdraftLimit();

    // ------------------------------------------------------------------
    // Getters / validated setter (ENCAPSULATION)
    // ------------------------------------------------------------------

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getOwnerName() {
        return ownerName;
    }

    /** final: it is called from the constructor, so it must not be overridable. */
    public final void setOwnerName(String ownerName) {
        if (ownerName == null || !NAME_PATTERN.matcher(ownerName.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Owner name must be 2-50 characters (letters, spaces, . ' - only).");
        }
        this.ownerName = ownerName.trim();
    }

    @Override
    public BigDecimal getBalance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock(); // ALWAYS unlock in finally, even if an exception is thrown
        }
    }

    /** Balance plus overdraft room. */
    public BigDecimal getAvailableFunds() {
        return getBalance().add(getOverdraftLimit());
    }

    // ------------------------------------------------------------------
    // Money movement (thread-safe)
    // ------------------------------------------------------------------

    @Override
    public void deposit(BigDecimal amount) {
        BigDecimal value = Money.requirePositive(amount);
        lock.lock();
        try {
            balance = balance.add(value);   // read-modify-write: must be atomic
        } finally {
            lock.unlock();
        }
    }

    /** TEMPLATE METHOD: final algorithm, subclass-specific limits. */
    @Override
    public final void withdraw(BigDecimal amount) throws InsufficientBalanceException {
        BigDecimal value = Money.requirePositive(amount);
        if (value.compareTo(getMaxWithdrawalPerTransaction()) > 0) {
            throw new IllegalArgumentException("Withdrawal exceeds the %s limit of %s per transaction."
                    .formatted(getType(), getMaxWithdrawalPerTransaction()));
        }
        lock.lock();
        try {
            BigDecimal floor = getOverdraftLimit().negate();
            BigDecimal projected = balance.subtract(value);
            if (projected.compareTo(floor) < 0) {
                throw new InsufficientBalanceException(accountNumber, balance.add(getOverdraftLimit()), value);
            }
            balance = projected;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically moves money between two accounts.
     *
     * DEADLOCK PREVENTION: if thread 1 does A->B while thread 2 does B->A and each
     * grabs its "own" account first, both wait forever. Always locking the account
     * with the SMALLER number first gives every thread the same lock order, so a
     * circular wait is impossible.
     */
    public static void transfer(Account from, Account to, BigDecimal amount)
            throws InsufficientBalanceException {
        if (from.accountNumber.equals(to.accountNumber)) {
            throw new IllegalArgumentException("Cannot transfer to the same account.");
        }
        Account first = from.accountNumber.compareTo(to.accountNumber) < 0 ? from : to;
        Account second = (first == from) ? to : from;

        first.lock.lock();
        try {
            second.lock.lock();
            try {
                from.withdraw(amount);   // throws before anything changes if funds are short
                to.deposit(amount);      // so the transfer is all-or-nothing
            } finally {
                second.lock.unlock();
            }
        } finally {
            first.lock.unlock();
        }
    }

    /** Applies this month's interest/charge and returns the signed amount applied. */
    public BigDecimal applyMonthlyInterest() {
        lock.lock();
        try {
            BigDecimal interest = calculateMonthlyInterest(); // polymorphic call
            balance = balance.add(interest);
            return interest;
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Auditable
    // ------------------------------------------------------------------

    @Override
    public void recordAudit(Transaction transaction) {
        synchronized (auditTrail) {
            auditTrail.add(transaction);
        }
    }

    @Override
    public List<Transaction> getAuditTrail() {
        synchronized (auditTrail) {
            return List.copyOf(auditTrail);   // immutable snapshot
        }
    }

    // ------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Account other && accountNumber.equals(other.accountNumber));
    }

    @Override
    public int hashCode() {
        return accountNumber.hashCode();
    }

    @Override
    public String toString() {
        return "%-8s | %-8s | %-22s | %12s".formatted(accountNumber, getType(), ownerName, getBalance());
    }
}
