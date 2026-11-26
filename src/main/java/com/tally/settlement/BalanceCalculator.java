package com.tally.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out each member's net balance: what they paid minus their share of what they took part in.
 * A positive balance means the group owes them money; a negative one means they owe the group.
 *
 * <p>All arithmetic is done in whole cents, so no rounding error can creep in. When an amount
 * does not divide evenly, the leftover cents go to the participants with the lowest ids,
 * which keeps the result deterministic and the total exact.
 */
public final class BalanceCalculator {

    private BalanceCalculator() {
    }

    public static Map<Long, BigDecimal> netBalances(Collection<Long> memberIds,
                                                    Collection<ExpenseShare> expenses) {
        Map<Long, Long> cents = new LinkedHashMap<>();
        memberIds.forEach(id -> cents.put(id, 0L));

        for (ExpenseShare expense : expenses) {
            long total = Money.toCents(expense.amount());
            cents.merge(expense.payerId(), total, Long::sum);
            splitEvenly(total, expense.participantIds())
                    .forEach((memberId, share) -> cents.merge(memberId, -share, Long::sum));
        }

        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        cents.forEach((id, value) -> balances.put(id, Money.fromCents(value)));
        return balances;
    }

    /** Splits a total in cents into equal shares that always add back up to the total. */
    static Map<Long, Long> splitEvenly(long totalCents, List<Long> participantIds) {
        List<Long> ordered = participantIds.stream().distinct().sorted().toList();
        long base = totalCents / ordered.size();
        long remainder = totalCents % ordered.size();

        Map<Long, Long> shares = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            shares.put(ordered.get(i), base + (i < remainder ? 1 : 0));
        }
        return shares;
    }

    /** Small helpers for moving between {@link BigDecimal} amounts and whole cents. */
    static final class Money {
        private Money() {
        }

        static long toCents(BigDecimal amount) {
            return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        }

        static BigDecimal fromCents(long cents) {
            return BigDecimal.valueOf(cents, 2);
        }
    }
}
