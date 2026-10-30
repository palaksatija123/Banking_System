package com.bank.model;

import com.bank.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** INHERITANCE + POLYMORPHISM: earns interest, no overdraft, lower withdrawal limit. */
public class SavingsAccount extends Account {

    private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.04");        // 4% p.a.
    private static final BigDecimal MAX_WITHDRAWAL = new BigDecimal("10000.00");

    public SavingsAccount(String accountNumber, String ownerName, BigDecimal openingBalance) {
        super(accountNumber, ownerName, openingBalance);
    }

    @Override
    public AccountType getType() {
        return AccountType.SAVINGS;
    }

    @Override
    protected BigDecimal calculateMonthlyInterest() {
        BigDecimal balance = getBalance();
        if (balance.signum() <= 0) {
            return Money.ZERO;
        }
        return balance.multiply(ANNUAL_RATE).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal getMaxWithdrawalPerTransaction() {
        return MAX_WITHDRAWAL;
    }

    @Override
    public BigDecimal getOverdraftLimit() {
        return Money.ZERO;
    }
}
