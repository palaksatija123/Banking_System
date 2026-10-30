package com.bank.service;

import com.bank.exception.InsufficientBalanceException;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.SavingsAccount;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MULTITHREADING DEMOS (run from the CLI menu).
 *
 * Demo 1 - Race condition: the SAME work is done by 8 threads on
 *   (a) an unsynchronised counter  -> updates are LOST
 *   (b) a 'synchronized' counter   -> correct
 *   (c) the real Account (ReentrantLock) -> correct
 *
 * Demo 2 - Concurrent transfers: 4 threads move money back and forth between
 *   two accounts. If locking were wrong we would see lost money or a deadlock.
 */
public final class ConcurrencyDemo {

    private static final int THREADS = 8;
    private static final int DEPOSITS_PER_THREAD = 50_000;

    private ConcurrencyDemo() { }

    // ------------------------------------------------------------------
    // Demo 1: race condition
    // ------------------------------------------------------------------

    /** BROKEN on purpose. "balance = balance + amount" is three steps: read, add, write. */
    private static final class UnsafeBalance {
        private long balance;

        void deposit(long amount) {
            long current = balance;          // 1. READ
            // Thread.yield() is added ONLY to make this demo reliable. Real race conditions are
            // timing-dependent and may hide for months; yielding between the read and the write
            // widens the window so the lost updates show up every single run.
            Thread.yield();
            balance = current + amount;      // 2. ADD + 3. WRITE: may overwrite another thread's update
        }

        long get() {
            return balance;
        }
    }

    /** FIXED with the 'synchronized' keyword: only one thread at a time may run these methods. */
    private static final class SynchronizedBalance {
        private long balance;

        synchronized void deposit(long amount) {
            balance += amount;
        }

        synchronized long get() {
            return balance;
        }
    }

    public static void raceConditionDemo() throws InterruptedException {
        long expected = (long) THREADS * DEPOSITS_PER_THREAD;
        System.out.printf("%d threads each deposit 1 unit %,d times. Expected final balance: %,d%n%n",
                THREADS, DEPOSITS_PER_THREAD, expected);

        // (a) Unsafe, using raw Threads + Runnable
        UnsafeBalance unsafe = new UnsafeBalance();
        runOnRawThreads(() -> {
            for (int i = 0; i < DEPOSITS_PER_THREAD; i++) {
                unsafe.deposit(1);
            }
        });
        long unsafeResult = unsafe.get();
        System.out.printf("(a) NO synchronisation   : %,d   -> %,d updates LOST  <-- race condition!%n",
                unsafeResult, expected - unsafeResult);

        // (b) synchronized, using an ExecutorService
        SynchronizedBalance safe = new SynchronizedBalance();
        runOnExecutor(() -> {
            for (int i = 0; i < DEPOSITS_PER_THREAD; i++) {
                safe.deposit(1);
            }
        });
        System.out.printf("(b) synchronized         : %,d   -> %s%n", safe.get(),
                safe.get() == expected ? "correct" : "WRONG");

        // (c) the real Account class, protected by a ReentrantLock
        Account account = new SavingsAccount("ACC9999", "Demo User", BigDecimal.ZERO);
        runOnExecutor(() -> {
            for (int i = 0; i < DEPOSITS_PER_THREAD; i++) {
                account.deposit(BigDecimal.ONE);
            }
        });
        System.out.printf("(c) Account + ReentrantLock: %,d -> %s%n", account.getBalance().longValue(),
                account.getBalance().longValue() == expected ? "correct" : "WRONG");

        System.out.println("\nWhy (a) loses updates: 'balance = balance + 1' is read -> add -> write.");
        System.out.println("Two threads can read the same old value and both write old+1, so one deposit vanishes.");
        System.out.println("A lock/synchronized makes the three steps ONE indivisible (atomic) action.");
    }

    /** Starts THREADS plain Thread objects, releases them together with a latch, waits with join(). */
    private static void runOnRawThreads(Runnable work) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Thread thread = new Thread(() -> {
                try {
                    startGate.await();                  // all threads wait here...
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                work.run();
            }, "worker-" + i);
            threads.add(thread);
            thread.start();
        }
        startGate.countDown();                          // ...and are released at the same moment
        for (Thread thread : threads) {
            thread.join();                              // wait for each thread to finish
        }
    }

    /** Same idea with a thread pool. */
    private static void runOnExecutor(Runnable work) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            for (int i = 0; i < THREADS; i++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    work.run();
                });
            }
            startGate.countDown();
        } finally {
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.MINUTES);  // wait until all submitted tasks complete
        }
    }

    // ------------------------------------------------------------------
    // Demo 2: concurrent transfers
    // ------------------------------------------------------------------

    public static void transferSimulation() throws InterruptedException, ExecutionException {
        final int workers = 4;
        final int transfersPerWorker = 500;

        // A private BankService so this simulation never touches the real data.
        BankService bank = new BankService();
        Account a = bank.openAccount(AccountType.SAVINGS, "Alice Demo", new BigDecimal("10000"));
        Account b = bank.openAccount(AccountType.CHECKING, "Bob Demo", new BigDecimal("10000"));
        BigDecimal totalBefore = a.getBalance().add(b.getBalance());

        AtomicInteger succeeded = new AtomicInteger();   // lock-free thread-safe counters
        AtomicInteger rejected = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int w = 0; w < workers; w++) {
            tasks.add(() -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < transfersPerWorker; i++) {
                    boolean forward = random.nextBoolean();            // half A->B, half B->A
                    Account from = forward ? a : b;
                    Account to = forward ? b : a;
                    BigDecimal amount = BigDecimal.valueOf(random.nextInt(1, 200));
                    try {
                        bank.transfer(from.getAccountNumber(), to.getAccountNumber(), amount);
                        succeeded.incrementAndGet();
                    } catch (InsufficientBalanceException e) {
                        rejected.incrementAndGet();
                    }
                }
                return null;
            });
        }

        System.out.printf("%d threads x %d random transfers between %s and %s...%n",
                workers, transfersPerWorker, a.getAccountNumber(), b.getAccountNumber());

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            for (Future<Void> future : pool.invokeAll(tasks)) {
                future.get();                                          // surfaces any worker exception
            }
        } finally {
            pool.shutdown();
        }

        BigDecimal totalAfter = a.getBalance().add(b.getBalance());
        System.out.printf("Transfers succeeded : %d%n", succeeded.get());
        System.out.printf("Transfers rejected  : %d (insufficient funds)%n", rejected.get());
        System.out.printf("%s balance      : %s%n", a.getAccountNumber(), a.getBalance());
        System.out.printf("%s balance      : %s%n", b.getAccountNumber(), b.getBalance());
        System.out.printf("Total before / after: %s / %s  -> %s%n", totalBefore, totalAfter,
                totalBefore.compareTo(totalAfter) == 0 ? "money conserved, no deadlock" : "MONEY LOST!");
        System.out.printf("Ledger entries      : %d%n", bank.getLedger().size());
    }
}
