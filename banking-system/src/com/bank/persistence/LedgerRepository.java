package com.bank.persistence;

import com.bank.model.Account;
import com.bank.model.Transaction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Persistence facade: one place that knows where the ledger files live.
 * Uses two instances of the generic DataStore<T>.
 *
 *   data/accounts.csv      - one line per account (current balance)
 *   data/transactions.csv  - one line per transaction (full history)
 */
public class LedgerRepository {

    private final DataStore<Account> accountStore;
    private final DataStore<Transaction> transactionStore;

    public LedgerRepository(Path directory) {
        // Method references (Class::method) are a compact form of lambda.
        this.accountStore = new DataStore<>(directory.resolve("accounts.csv"),
                AccountCodec.HEADER, AccountCodec::encode, AccountCodec::decode);
        this.transactionStore = new DataStore<>(directory.resolve("transactions.csv"),
                Transaction.CSV_HEADER, Transaction::toCsv, Transaction::fromCsv);
    }

    public List<Account> loadAccounts() throws IOException {
        return accountStore.loadAll();
    }

    public List<Transaction> loadTransactions() throws IOException {
        return transactionStore.loadAll();
    }

    public void save(Collection<Account> accounts, Collection<Transaction> transactions) throws IOException {
        accountStore.saveAll(accounts.stream()
                .sorted(Comparator.comparing(Account::getAccountNumber))
                .toList());
        transactionStore.saveAll(transactions);
    }
}
