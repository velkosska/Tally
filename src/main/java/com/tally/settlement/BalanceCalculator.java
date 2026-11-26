package com.tally.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Works out each member's net balance: what they paid minus their share of what they took part in.
 * A positive balance means the group owes them money; a negative one means they owe the group.
 *
 * <p>All arithmetic is done in whole cents, so no rounding error can creep in. Each expense is divided
 * by {@link SplitCalculator}, whose shares always add up to the expense amount exactly.
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
            SplitCalculator.split(total, expense.method(), expense.weights())
                    .forEach((memberId, share) -> cents.merge(memberId, -share, Long::sum));
        }

        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        cents.forEach((id, value) -> balances.put(id, Money.fromCents(value)));
        return balances;
    }

    /** Small helpers for moving between {@link BigDecimal} amounts and whole cents. */
    public static final class Money {
        private Money() {
        }

        public static long toCents(BigDecimal amount) {
            return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        }

        public static BigDecimal fromCents(long cents) {
            return BigDecimal.valueOf(cents, 2);
        }
    }
}
