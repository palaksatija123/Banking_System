package com.bank.service;

import com.bank.exception.InsufficientBalanceException;
import com.bank.exception.InvalidAccountException;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.model.TransactionStatus;
import com.bank.model.TransactionType;
import com.bank.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The core business service. All the public methods are safe to call from many
 * threads at the same time.
 *
 * Collections used
 * ----------------
 * - Map<String, Account>   : ConcurrentHashMap, a thread-safe map (lookup by number)
 * - List<Transaction>      : the ledger; an ArrayList guarded by 'synchronized'
 *
 * Every attempt (success OR failure) is written to the ledger, which is what a
 * real bank's audit log does.
 */
public class BankService {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final List<Transaction> ledger = new ArrayList<>();   // guarded by 'ledger'
    private final AtomicLong accountSequence = new AtomicLong(1000);
    private final AtomicLong transactionSequence = new AtomicLong(0);

    // ------------------------------------------------------------------
    // Accounts
    // ------------------------------------------------------------------

    public Account openAccount(AccountType type, String ownerName, BigDecimal initialDeposit) {
        if (type == null) {
            throw new IllegalArgumentException("Account type is required.");
        }
        if (initialDeposit != null && initialDeposit.signum() < 0) {
            throw new IllegalArgumentException("Initial deposit cannot be negative.");
        }
        String number = "ACC" + accountSequence.incrementAndGet();   // atomic: no duplicate numbers
        Account account = type.create(number, ownerName, Money.ZERO);
        accounts.put(number, account);
        if (initialDeposit != null && initialDeposit.signum() > 0) {
            deposit(number, initialDeposit, "Opening deposit");
        }
        return account;
    }

    /** @throws InvalidAccountException (unchecked) when the number is blank or unknown */
    public Account getAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new InvalidAccountException("Account number is required.");
        }
        Account account = accounts.get(accountNumber.trim().toUpperCase(Locale.ROOT));
        if (account == null) {
            throw new InvalidAccountException("No such account: " + accountNumber.trim());
        }
        return account;
    }

    public List<Account> getAccounts() {
        return accounts.values().stream()
                .sorted(Comparator.comparing(Account::getAccountNumber))
                .toList();
    }

    // ------------------------------------------------------------------
    // Money movement
    // ------------------------------------------------------------------

    public Transaction deposit(String accountNumber, BigDecimal amount) {
        return deposit(accountNumber, amount, "Deposit");
    }

    private Transaction deposit(String accountNumber, BigDecimal amount, String note) {
        BigDecimal value = Money.requirePositive(amount);
        Account account = getAccount(accountNumber);
        account.deposit(value);
        return log(TransactionType.DEPOSIT, Transaction.NONE, account.getAccountNumber(),
                value, TransactionStatus.SUCCESS, note);
    }

    public Transaction withdraw(String accountNumber, BigDecimal amount) throws InsufficientBalanceException {
        BigDecimal value = Money.requirePositive(amount);
        Account account = getAccount(accountNumber);
        try {
            account.withdraw(value);
            return log(TransactionType.WITHDRAW, account.getAccountNumber(), Transaction.NONE,
                    value, TransactionStatus.SUCCESS, "Withdrawal");
        } catch (InsufficientBalanceException | IllegalArgumentException e) {
            // Record the failed attempt, then re-throw so the caller can react.
            log(TransactionType.WITHDRAW, account.getAccountNumber(), Transaction.NONE,
                    value, TransactionStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    public Transaction transfer(String fromNumber, String toNumber, BigDecimal amount)
            throws InsufficientBalanceException {
        BigDecimal value = Money.requirePositive(amount);
        Account from = getAccount(fromNumber);
        Account to = getAccount(toNumber);
        try {
            Account.transfer(from, to, value);
            return log(TransactionType.TRANSFER, from.getAccountNumber(), to.getAccountNumber(),
                    value, TransactionStatus.SUCCESS, "Transfer");
        } catch (InsufficientBalanceException | IllegalArgumentException e) {
            log(TransactionType.TRANSFER, from.getAccountNumber(), to.getAccountNumber(),
                    value, TransactionStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    /**
     * Applies monthly interest to every account. Each subclass computes its own
     * amount (polymorphism); this method neither knows nor cares which is which.
     */
    public List<Transaction> applyInterestToAll() {
        List<Transaction> created = new ArrayList<>();
        for (Account account : getAccounts()) {
            BigDecimal interest = account.applyMonthlyInterest();
            if (interest.signum() > 0) {
                created.add(log(TransactionType.INTEREST, Transaction.NONE, account.getAccountNumber(),
                        interest, TransactionStatus.SUCCESS, "Interest credited"));
            } else if (interest.signum() < 0) {
                created.add(log(TransactionType.INTEREST, account.getAccountNumber(), Transaction.NONE,
                        interest.abs(), TransactionStatus.SUCCESS, "Overdraft interest charged"));
            }
        }
        return created;
    }

    // ------------------------------------------------------------------
    // Ledger
    // ------------------------------------------------------------------

    /** Immutable snapshot of the ledger, safe to stream without holding a lock. */
    public List<Transaction> getLedger() {
        synchronized (ledger) {
            return List.copyOf(ledger);
        }
    }

    private Transaction log(TransactionType type, String from, String to, BigDecimal amount,
                            TransactionStatus status, String note) {
        Transaction tx = new Transaction(
                "TXN-%06d".formatted(transactionSequence.incrementAndGet()),
                type, from, to, amount, LocalDateTime.now(), status, note);
        synchronized (ledger) {
            ledger.add(tx);
        }
        attachToAccounts(tx);
        return tx;
    }

    private void attachToAccounts(Transaction tx) {
        // A Set removes the duplicate when from == to, so the audit entry is recorded once.
        for (String number : new java.util.LinkedHashSet<>(List.of(tx.fromAccount(), tx.toAccount()))) {
            Account account = accounts.get(number);
            if (account != null) {
                account.recordAudit(tx);
            }
        }
    }

    /** Re-hydrates the service from saved data (called once at start-up). */
    public void restore(Collection<Account> loadedAccounts, Collection<Transaction> loadedTransactions) {
        for (Account account : loadedAccounts) {
            accounts.put(account.getAccountNumber(), account);
        }
        synchronized (ledger) {
            ledger.addAll(loadedTransactions);
        }
        loadedTransactions.forEach(this::attachToAccounts);

        // Continue numbering after the highest id we have seen.
        accountSequence.accumulateAndGet(
                loadedAccounts.stream().mapToLong(a -> numericPart(a.getAccountNumber())).max().orElse(0),
                Math::max);
        transactionSequence.accumulateAndGet(
                loadedTransactions.stream().mapToLong(t -> numericPart(t.id())).max().orElse(0),
                Math::max);
    }

    private static long numericPart(String id) {
        String digits = id.replaceAll("\\D", "");
        return digits.isEmpty() ? 0 : Long.parseLong(digits);
    }
}
