package com.bank;

import com.bank.exception.InsufficientBalanceException;
import com.bank.exception.InvalidAccountException;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.persistence.LedgerRepository;
import com.bank.service.BankService;
import com.bank.service.ConcurrencyDemo;
import com.bank.service.TransactionJob;
import com.bank.service.TransactionProcessor;
import com.bank.service.TransactionReport;
import com.bank.util.Money;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

/**
 * Entry point: an interactive console menu driven by java.util.Scanner.
 *
 * Main only handles user interaction (read input, print output). All business
 * rules live in the service/model packages, which keeps the layers separate.
 */
public final class Main {

    private static final Path DATA_DIR = Path.of("data");
    private static final int WORKER_THREADS = 3;

    private final Scanner in = new Scanner(System.in);
    private final BankService bank = new BankService();
    private final TransactionProcessor processor = new TransactionProcessor(bank);
    private final LedgerRepository repository = new LedgerRepository(DATA_DIR);

    public static void main(String[] args) {
        new Main().run();
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    private void run() {
        System.out.println("Multi-Threaded Banking & Transaction Management System");
        loadData();
        try {
            boolean running = true;
            while (running) {
                printMenu();
                running = handle(prompt("Choose an option"));
            }
        } catch (NoSuchElementException endOfInput) {
            System.out.println("\nInput closed - shutting down.");     // e.g. Ctrl+D or piped input ended
        } finally {
            saveData();          // FINALLY: runs whether we exit normally or because of an error
            in.close();
        }
        System.out.println("Goodbye!");
    }

    private void loadData() {
        try {
            List<Account> accounts = repository.loadAccounts();
            List<Transaction> transactions = repository.loadTransactions();
            bank.restore(accounts, transactions);
            if (accounts.isEmpty() && transactions.isEmpty()) {
                System.out.println("No saved data found - starting with an empty bank.");
            } else {
                System.out.printf("Loaded %d account(s) and %d transaction(s) from '%s'.%n",
                        accounts.size(), transactions.size(), DATA_DIR);
            }
        } catch (IOException e) {
            System.err.println("Could not read saved data (" + e.getMessage() + "). Starting empty.");
        }
    }

    private void saveData() {
        try {
            repository.save(bank.getAccounts(), bank.getLedger());
            System.out.println("Data saved to '" + DATA_DIR + "'.");
        } catch (IOException e) {
            System.err.println("ERROR: could not save data: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Menu dispatch + centralised exception handling
    // ------------------------------------------------------------------

    private void printMenu() {
        System.out.println("""

                ================= MAIN MENU =================
                 1. Create account            8. Apply monthly interest
                 2. Deposit                   9. Queue a background job
                 3. Withdraw                 10. Process queued jobs (thread pool)
                 4. Transfer                 11. Race-condition demo
                 5. Account details          12. Concurrent transfer simulation
                 6. List all accounts        13. Save data now
                 7. Transaction history      14. Load demo data
                 0. Save and exit
                =============================================""");
    }

    /** @return false when the user asked to exit */
    private boolean handle(String choice) {
        try {
            switch (choice) {
                case "1" -> createAccount();
                case "2" -> deposit();
                case "3" -> withdraw();
                case "4" -> transfer();
                case "5" -> accountDetails();
                case "6" -> listAccounts();
                case "7" -> historyMenu();
                case "8" -> applyInterest();
                case "9" -> queueJob();
                case "10" -> processQueue();
                case "11" -> ConcurrencyDemo.raceConditionDemo();
                case "12" -> ConcurrencyDemo.transferSimulation();
                case "13" -> saveData();
                case "14" -> loadDemoData();
                case "0" -> {
                    return false;
                }
                default -> System.out.println("Unknown option '" + choice + "'.");
            }
        } catch (InsufficientBalanceException e) {          // CHECKED: compiler forced us to handle it
            System.out.println("  ! Declined: " + e.getMessage());
            System.out.println("    Short by " + e.getShortfall() + ".");
        } catch (InvalidAccountException e) {               // UNCHECKED: handled by choice
            System.out.println("  ! Account problem: " + e.getMessage());
        } catch (IllegalArgumentException e) {              // bad input / rule violation
            System.out.println("  ! Invalid input: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();             // restore the interrupt flag
            System.out.println("  ! Interrupted.");
        } catch (ExecutionException e) {
            System.out.println("  ! A worker thread failed: " + e.getCause());
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void createAccount() {
        AccountType type = switch (prompt("Account type [1=Savings, 2=Checking]")) {
            case "1" -> AccountType.SAVINGS;
            case "2" -> AccountType.CHECKING;
            default -> throw new IllegalArgumentException("Choose 1 or 2.");
        };
        String owner = prompt("Owner name");
        BigDecimal initial = readAmount("Initial deposit (0 for none)");
        Account account = bank.openAccount(type, owner, initial);
        System.out.println("  Created: " + account);
    }

    private void deposit() {
        Transaction tx = bank.deposit(prompt("Account number"), readAmount("Amount"));
        System.out.println("  Done: " + TransactionReport.format(tx));
    }

    private void withdraw() throws InsufficientBalanceException {
        Transaction tx = bank.withdraw(prompt("Account number"), readAmount("Amount"));
        System.out.println("  Done: " + TransactionReport.format(tx));
    }

    private void transfer() throws InsufficientBalanceException {
        String from = prompt("From account");
        String to = prompt("To account");
        Transaction tx = bank.transfer(from, to, readAmount("Amount"));
        System.out.println("  Done: " + TransactionReport.format(tx));
    }

    private void accountDetails() {
        Account account = bank.getAccount(prompt("Account number"));
        System.out.println("  " + account);
        System.out.println("  Max per withdrawal : " + account.getMaxWithdrawalPerTransaction());
        System.out.println("  Overdraft limit    : " + account.getOverdraftLimit());
        System.out.println("  Available funds    : " + account.getAvailableFunds());
        System.out.println("  Audit trail        : " + account.auditSummary());   // default interface method
        System.out.println("  Latest activity:");
        TransactionReport.print(TransactionReport.forAccount(
                account.getAuditTrail(), account.getAccountNumber(), TransactionReport.NEWEST_FIRST)
                .stream().limit(10).toList());
    }

    private void listAccounts() {
        List<Account> accounts = bank.getAccounts();
        if (accounts.isEmpty()) {
            System.out.println("  (no accounts yet)");
            return;
        }
        System.out.printf("  %-8s | %-8s | %-22s | %12s%n", "NUMBER", "TYPE", "OWNER", "BALANCE");
        accounts.forEach(a -> System.out.println("  " + a));
        BigDecimal total = accounts.stream().map(Account::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("  Total holdings: " + Money.normalize(total));
    }

    private void applyInterest() {
        List<Transaction> created = bank.applyInterestToAll();
        System.out.println("  Interest applied (savings earn, checking earns/pays by rule):");
        TransactionReport.print(created);
    }

    // ------------------------------------------------------------------
    // History: filter / sort with streams and lambdas
    // ------------------------------------------------------------------

    private void historyMenu() {
        List<Transaction> all = bank.getLedger();       // immutable snapshot
        System.out.println("""
                  History:  1=All  2=By account  3=By type  4=Minimum amount
                            5=Failed only  6=Top 5 largest  7=Totals & statistics""");
        switch (prompt("Choice")) {
            case "1" -> TransactionReport.print(TransactionReport.filter(all, t -> true, chooseOrdering()));
            case "2" -> {
                String number = bank.getAccount(prompt("Account number")).getAccountNumber();
                TransactionReport.print(TransactionReport.forAccount(all, number, chooseOrdering()));
            }
            case "3" -> {
                TransactionType type = TransactionType.valueOf(
                        prompt("Type (DEPOSIT, WITHDRAW, TRANSFER, INTEREST)").toUpperCase());
                TransactionReport.print(TransactionReport.ofType(all, type, chooseOrdering()));
            }
            case "4" -> {
                BigDecimal min = readAmount("Minimum amount");
                Predicate<Transaction> big = t -> t.amount().compareTo(min) >= 0;   // lambda as a Predicate
                TransactionReport.print(TransactionReport.filter(all, big, chooseOrdering()));
            }
            case "5" -> TransactionReport.print(TransactionReport.failed(all, chooseOrdering()));
            case "6" -> TransactionReport.print(TransactionReport.largest(all, 5));
            case "7" -> {
                System.out.println("  Successful totals by type:");
                TransactionReport.totalsByType(all).forEach((type, sum) ->
                        System.out.printf("    %-10s %14s%n", type, sum));
                System.out.println("  Counts by status: " + TransactionReport.countByStatus(all));
            }
            default -> System.out.println("  Unknown choice.");
        }
    }

    private Comparator<Transaction> chooseOrdering() {
        return switch (prompt("Sort: 1=newest first, 2=oldest first, 3=largest first [1]")) {
            case "2" -> TransactionReport.OLDEST_FIRST;
            case "3" -> TransactionReport.LARGEST_FIRST;
            default -> TransactionReport.NEWEST_FIRST;
        };
    }

    // ------------------------------------------------------------------
    // Background jobs (Queue + ExecutorService)
    // ------------------------------------------------------------------

    private void queueJob() {
        TransactionType type = switch (prompt("Job type [1=Deposit, 2=Withdraw, 3=Transfer]")) {
            case "1" -> TransactionType.DEPOSIT;
            case "2" -> TransactionType.WITHDRAW;
            case "3" -> TransactionType.TRANSFER;
            default -> throw new IllegalArgumentException("Choose 1, 2 or 3.");
        };
        String from = (type == TransactionType.DEPOSIT) ? null : prompt("From account");
        String to = (type == TransactionType.WITHDRAW) ? null : prompt("To account");
        processor.enqueue(new TransactionJob(type, from, to, readAmount("Amount")));
        System.out.println("  Queued. Pending jobs: " + processor.pendingJobs().size());
        processor.pendingJobs().forEach(job -> System.out.println("    - " + job.describe()));
    }

    private void processQueue() throws InterruptedException {
        int count = processor.pendingJobs().size();
        if (count == 0) {
            System.out.println("  The queue is empty. Use option 9 to add jobs.");
            return;
        }
        System.out.println("  Processing " + count + " job(s) on a pool of up to " + WORKER_THREADS + " threads...");
        processor.processAll(WORKER_THREADS).forEach(line -> System.out.println("  " + line));
    }

    private void loadDemoData() throws InsufficientBalanceException {
        Account alice = bank.openAccount(AccountType.SAVINGS, "Alice Johnson", new BigDecimal("5000"));
        Account bob = bank.openAccount(AccountType.CHECKING, "Bob Smith", new BigDecimal("1500"));
        Account carol = bank.openAccount(AccountType.SAVINGS, "Carol Davis", new BigDecimal("12000"));
        bank.transfer(alice.getAccountNumber(), bob.getAccountNumber(), new BigDecimal("250"));
        bank.withdraw(carol.getAccountNumber(), new BigDecimal("400"));
        bank.deposit(bob.getAccountNumber(), new BigDecimal("75.50"));
        try {
            bank.withdraw(alice.getAccountNumber(), new BigDecimal("8000"));   // deliberately too large
        } catch (InsufficientBalanceException e) {
            System.out.println("  (Demo: one withdrawal was declined on purpose to create a FAILED entry.)");
        }
        System.out.println("  Demo data loaded: " + alice.getAccountNumber() + ", "
                + bob.getAccountNumber() + ", " + carol.getAccountNumber());
    }

    // ------------------------------------------------------------------
    // Console helpers
    // ------------------------------------------------------------------

    private String prompt(String label) {
        System.out.print(label + ": ");
        return in.nextLine().trim();
    }

    /** Keeps asking until the user types a parseable number. */
    private BigDecimal readAmount(String label) {
        while (true) {
            try {
                return Money.parse(prompt(label));
            } catch (IllegalArgumentException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }
}
