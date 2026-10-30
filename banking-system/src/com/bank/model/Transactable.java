package com.bank.model;

import com.bank.exception.InsufficientBalanceException;

import java.math.BigDecimal;

/**
 * ABSTRACTION: describes WHAT something that holds money can do, without
 * saying HOW. Callers depend on this contract, not on a concrete class.
 */
public interface Transactable {

    void deposit(BigDecimal amount);

    void withdraw(BigDecimal amount) throws InsufficientBalanceException;

    BigDecimal getBalance();
}
