package com.bank.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Small helper for money handling.
 *
 * WHY BigDecimal? double/float cannot represent 0.10 exactly, so repeated
 * arithmetic drifts. Banking code always uses BigDecimal with a fixed scale.
 */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private Money() { }

    /** Forces exactly 2 decimal places (HALF_UP rounding). */
    public static BigDecimal normalize(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Parses user/file text into a normalized amount. */
    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Amount is required.");
        }
        try {
            return normalize(new BigDecimal(text.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a valid amount: '" + text + "'");
        }
    }

    /** Validates that an amount is strictly positive (after rounding to cents). */
    public static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required.");
        }
        BigDecimal normalized = normalize(amount);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        return normalized;
    }
}
