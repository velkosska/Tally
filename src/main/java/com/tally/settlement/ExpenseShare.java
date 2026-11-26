package com.tally.settlement;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * The minimal facts the balance calculation needs about one expense:
 * who paid, how much, and who it is split between (equally).
 */
public record ExpenseShare(long payerId, BigDecimal amount, List<Long> participantIds) {

    public ExpenseShare {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Expense amount must be positive");
        }
        if (participantIds == null || participantIds.isEmpty()) {
            throw new IllegalArgumentException("An expense needs at least one participant");
        }
        participantIds = List.copyOf(participantIds);
    }
}
