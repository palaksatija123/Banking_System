package com.bank.model;

import com.bank.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** INHERITANCE + POLYMORPHISM: overdraft allowed, tiny interest, higher withdrawal limit. */
public class CheckingAccount extends Account {

    private static final BigDecimal CREDIT_RATE = new BigDecimal("0.005");     // 0.5% p.a. if >= 5000
    private static final BigDecimal OVERDRAFT_RATE = new BigDecimal("0.12");   // 12% p.a. charged on debt
    private static final BigDecimal CREDIT_THRESHOLD = new BigDecimal("5000.00");
    private static final BigDecimal MAX_WITHDRAWAL = new BigDecimal("25000.00");
    private static final BigDecimal OVERDRAFT_LIMIT = new BigDecimal("1000.00");
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    public CheckingAccount(String accountNumber, String ownerName, BigDecimal openingBalance) {
        super(accountNumber, ownerName, openingBalance);
    }

    @Override
    public AccountType getType() {
        return AccountType.CHECKING;
    }

    @Override
    protected BigDecimal calculateMonthlyInterest() {
        BigDecimal balance = getBalance();
        if (balance.signum() < 0) {
            // Overdrawn: returns a NEGATIVE number, i.e. a charge.
            return balance.multiply(OVERDRAFT_RATE).divide(TWELVE, 2, RoundingMode.HALF_UP);
        }
        if (balance.compareTo(CREDIT_THRESHOLD) >= 0) {
            return balance.multiply(CREDIT_RATE).divide(TWELVE, 2, RoundingMode.HALF_UP);
        }
        return Money.ZERO;
    }

    @Override
    public BigDecimal getMaxWithdrawalPerTransaction() {
        return MAX_WITHDRAWAL;
    }

    @Override
    public BigDecimal getOverdraftLimit() {
        return OVERDRAFT_LIMIT;
    }
}
