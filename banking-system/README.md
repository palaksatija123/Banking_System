# Multi-Threaded Banking & Transaction Management System

A console-based **Core Java** project (Java 17+, tested on JDK 21). No frameworks, no external
libraries, no build tool: just `javac` and `java`.

## 1. Architecture overview

```
 Main (CLI, Scanner)                 <- user interaction only
   |
   +-- BankService                   <- business rules, thread-safe, writes the ledger
   |     +-- Map<String,Account>        (ConcurrentHashMap)
   |     +-- List<Transaction>          (ledger, guarded by synchronized)
   |
   +-- TransactionProcessor          <- Queue<TransactionJob> + ExecutorService
   +-- TransactionReport             <- Streams/lambdas: filter, sort, group
   +-- ConcurrencyDemo               <- race-condition + concurrent transfer demos
   |
   +-- LedgerRepository              <- persistence facade
         +-- DataStore<T> (generic, BufferedReader/BufferedWriter)
               +-- AccountCodec / Transaction.toCsv()/fromCsv()
```

Layers depend downward only: `Main -> service -> model`, and `persistence -> model`.
The model package knows nothing about the console or files.

## 2. Folder structure

```
banking-system/
├── README.md
├── build.sh / run.sh          (Linux/macOS)
├── build.bat / run.bat        (Windows)
├── src/com/bank/
│   ├── Main.java                         interactive menu (Scanner)
│   ├── model/
│   │   ├── Account.java                  abstract base class (encapsulation, template method, ReentrantLock)
│   │   ├── SavingsAccount.java           subclass: 4% interest, no overdraft, 10,000 limit
│   │   ├── CheckingAccount.java          subclass: overdraft 1,000, small interest, 25,000 limit
│   │   ├── AccountType.java              enum + factory
│   │   ├── Transactable.java             interface: deposit/withdraw/getBalance
│   │   ├── Auditable.java                interface with a default method
│   │   ├── Transaction.java              immutable RECORD (+ CSV conversion)
│   │   ├── TransactionType.java          enum DEPOSIT/WITHDRAW/TRANSFER/INTEREST
│   │   └── TransactionStatus.java        enum SUCCESS/FAILED
│   ├── exception/
│   │   ├── InsufficientBalanceException.java   CHECKED
│   │   └── InvalidAccountException.java        UNCHECKED
│   ├── service/
│   │   ├── BankService.java              core operations + ledger
│   │   ├── TransactionJob.java           record for queued work
│   │   ├── TransactionProcessor.java     Queue + ExecutorService + Callable/Future
│   │   ├── TransactionReport.java        streams, lambdas, comparators, collectors
│   │   └── ConcurrencyDemo.java          race-condition and transfer simulations
│   ├── persistence/
│   │   ├── DataStore.java                generic file store DataStore<T>
│   │   ├── AccountCodec.java             Account <-> CSV line
│   │   └── LedgerRepository.java         accounts.csv + transactions.csv
│   └── util/
│       ├── Money.java                    BigDecimal helpers
│       └── GenericUtils.java             generic methods groupBy / topN
└── data/                                 created at runtime (accounts.csv, transactions.csv)
```

## 3. Where each requirement is implemented

| Topic | Where | How |
|---|---|---|
| Encapsulation | `Account` | Private fields, validated `setOwnerName`, **no** `setBalance`: money moves only through validated methods |
| Inheritance / polymorphism | `Account` -> `SavingsAccount`, `CheckingAccount` | Subclasses override `calculateMonthlyInterest`, `getMaxWithdrawalPerTransaction`, `getOverdraftLimit`; `BankService.applyInterestToAll` calls them without knowing the subtype |
| Template method | `Account.withdraw` | `final` algorithm; limits come from abstract hooks |
| Abstraction | `Transactable`, `Auditable` | Contracts implemented by `Account`; `Auditable.auditSummary()` is a default method |
| Checked exception | `InsufficientBalanceException` | Extends `Exception`; callers must catch/declare it |
| Unchecked exception | `InvalidAccountException` | Extends `RuntimeException`; thrown for bad/unknown account numbers |
| try-catch-finally | `Main.run`, `Account` locks, `TransactionProcessor.processAll` | `finally` saves data on exit, releases locks, shuts the pool down |
| try-with-resources | `DataStore.loadAll/saveAll` | `BufferedReader`/`BufferedWriter` closed automatically |
| Thread-safe balance | `Account` | `ReentrantLock` around every read-modify-write |
| Deadlock avoidance | `Account.transfer` | Always lock the lower account number first |
| Runnable / Thread | `ConcurrencyDemo.runOnRawThreads` | Raw threads + `CountDownLatch` start gate + `join()` |
| ExecutorService | `ConcurrencyDemo`, `TransactionProcessor` | Fixed thread pools, `invokeAll`, `Callable`, `Future` |
| Race condition demo | menu option 11 | Unsafe vs `synchronized` vs `ReentrantLock` |
| List / Map / Queue | `BankService`, `TransactionProcessor` | `List<Transaction>`, `ConcurrentHashMap<String,Account>`, `ConcurrentLinkedQueue<TransactionJob>` |
| Generics | `DataStore<T>`, `GenericUtils` | One store class persists both Accounts and Transactions |
| File I/O | `DataStore`, `LedgerRepository` | CSV ledger loaded on start, saved on exit; atomic temp-file + move |
| Enums | `TransactionType`, `TransactionStatus`, `AccountType` | Fields, constructor, factory switch |
| Records | `Transaction`, `TransactionJob` | Immutable; compact constructors validate/normalise |
| Streams & lambdas | `TransactionReport`, `Main.historyMenu` | `Predicate`, `Comparator`, `groupingBy`, `reducing`, `limit` |
| CLI | `Main` | `Scanner`-driven menu with input validation |

## 4. Build and run

Requirements: JDK 17 or newer (`javac -version`).

```bash
# From the banking-system folder
mkdir -p out
javac -d out $(find src -name "*.java")
java -cp out com.bank.Main
```

Windows (Command Prompt):

```bat
mkdir out
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
java -cp out com.bank.Main
```

Or simply use `./build.sh && ./run.sh` (Linux/macOS) or `build.bat` then `run.bat` (Windows).

Data is stored in `./data/` relative to the folder you launch from, so always launch from the same folder.

## 5. Suggested walkthrough

1. Choose **14** (load demo data), then **6** to list accounts.
2. **4** transfer between two accounts, **3** withdraw too much to see the checked exception handled.
3. **5** account details (note the audit summary), **7** history: try filter by account, type, minimum amount, failed only, totals.
4. **9** queue a few jobs, then **10** to run them on a thread pool (see different `pool-1-thread-N` names).
5. **11** race-condition demo: (a) loses updates, (b) and (c) are correct.
6. **12** concurrent transfers: total money is conserved and nothing deadlocks.
7. **0** to save and exit, then run again: everything is reloaded from `data/`.

## 6. Design notes

* **BigDecimal for money**: `double` cannot represent 0.10 exactly.
* **Failed attempts are logged** too (status `FAILED`), like a real audit log.
* **Transfers are all-or-nothing**: both locks are held; `withdraw` throws before anything changes.
* **The race demo calls `Thread.yield()`** between the read and write of the unsafe counter to make the
  bug appear on every run. Real races are timing dependent. The exact number of lost updates varies
  by machine; it will never be zero.
* **Owner names** are restricted to letters/spaces/`. ' -` which also keeps the CSV format safe.

## 7. Ideas to extend

Scheduled interest with `ScheduledExecutorService`, a long-running background worker using
`BlockingQueue`, `CompletableFuture` pipelines, account freezing, JUnit tests, or a PIN per account.
