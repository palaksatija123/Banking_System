package com.bank.persistence;

import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.util.Money;

/** Converts an Account to/from one CSV line: type,accountNumber,ownerName,balance */
public final class AccountCodec {

    public static final String HEADER = "type,accountNumber,ownerName,balance";

    private AccountCodec() { }

    public static String encode(Account account) {
        return String.join(",",
                account.getType().name(),
                account.getAccountNumber(),
                account.getOwnerName(),
                account.getBalance().toPlainString());
    }

    public static Account decode(String line) {
        String[] p = line.split(",", -1);
        if (p.length != 4) {
            throw new IllegalArgumentException("Expected 4 fields but found " + p.length);
        }
        // AccountType.valueOf throws IllegalArgumentException for unknown types.
        return AccountType.valueOf(p[0]).create(p[1], p[2], Money.parse(p[3]));
    }
}
