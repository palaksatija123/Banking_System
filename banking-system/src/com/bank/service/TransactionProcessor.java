package com.bank.service;

import com.bank.exception.InsufficientBalanceException;
import com.bank.exception.InvalidAccountException;
import com.bank.model.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * QUEUE + EXECUTORSERVICE.
 *
 * Pending jobs wait in a Queue<TransactionJob> (FIFO: first in, first out).
 * ConcurrentLinkedQueue is thread-safe, so producers can enqueue while a
 * worker polls, without any extra locking.
 *
 * processAll() drains the queue and runs the jobs in parallel on a fixed
 * thread pool. Each job is a Callable (like Runnable, but returns a result and
 * may throw), and the Future lets us collect the result later.
 */
public class TransactionProcessor {

    private final BankService bank;
    private final Queue<TransactionJob> pending = new ConcurrentLinkedQueue<>();

    public TransactionProcessor(BankService bank) {
        this.bank = bank;
    }

    public void enqueue(TransactionJob job) {
        pending.offer(job);
    }

    public List<TransactionJob> pendingJobs() {
        return List.copyOf(pending);
    }

    /** Runs every queued job using {@code threads} worker threads; returns one result line per job. */
    public List<String> processAll(int threads) throws InterruptedException {
        List<Callable<String>> tasks = new ArrayList<>();
        TransactionJob job;
        while ((job = pending.poll()) != null) {          // poll() removes the head, null when empty
            final TransactionJob current = job;
            tasks.add(() -> execute(current));            // lambda implementing Callable<String>
        }
        List<String> results = new ArrayList<>();
        if (tasks.isEmpty()) {
            return results;
        }

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, tasks.size()));
        try {
            List<Future<String>> futures = pool.invokeAll(tasks);   // blocks until all are finished
            for (Future<String> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    results.add("ERROR  " + e.getCause());
                }
            }
        } finally {
            pool.shutdown();                                        // always release the threads
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
        return results;
    }

    /** Executes one job and converts every expected failure into a readable result line. */
    private String execute(TransactionJob job) {
        String worker = Thread.currentThread().getName();
        try {
            Transaction tx = switch (job.type()) {
                case DEPOSIT -> bank.deposit(job.toAccount(), job.amount());
                case WITHDRAW -> bank.withdraw(job.fromAccount(), job.amount());
                case TRANSFER -> bank.transfer(job.fromAccount(), job.toAccount(), job.amount());
                case INTEREST -> throw new IllegalArgumentException("Interest is not a queueable job.");
            };
            return "OK     [%s] %s -> %s".formatted(worker, job.describe(), tx.id());
        } catch (InsufficientBalanceException | InvalidAccountException | IllegalArgumentException e) {
            return "FAILED [%s] %s -> %s".formatted(worker, job.describe(), e.getMessage());
        }
    }
}
