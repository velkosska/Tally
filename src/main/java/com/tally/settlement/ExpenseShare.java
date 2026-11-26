package com.tally.settlement;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The minimal facts the balance calculation needs about one expense: who paid, how much,
 * and how it is split between the participants.
 *
 * @param weights participant id to their number for the split method (amount, percentage or shares);
 *                for an equal split only the ids matter
 */
public record ExpenseShare(long payerId, BigDecimal amount, SplitMethod method, Map<Long, BigDecimal> weights) {

    public ExpenseShare {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(method, "method");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Expense amount must be positive");
        }
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("An expense needs at least one participant");
        }
        weights = Map.copyOf(weights);
    }

    /** An expense split equally between the participants. */
    public ExpenseShare(long payerId, BigDecimal amount, List<Long> participantIds) {
        this(payerId, amount, SplitMethod.EQUAL, equalWeights(participantIds));
    }

    private static Map<Long, BigDecimal> equalWeights(List<Long> participantIds) {
        Map<Long, BigDecimal> weights = new LinkedHashMap<>();
        if (participantIds != null) {
            participantIds.forEach(id -> weights.put(id, BigDecimal.ONE));
        }
        return weights;
    }
}
