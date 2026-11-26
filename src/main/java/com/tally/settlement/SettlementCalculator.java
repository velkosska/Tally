package com.tally.settlement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Turns net balances into a short list of payments that settles everyone up.
 *
 * <p>Greedy strategy: repeatedly match the person who owes the most with the person who is
 * owed the most, and let them settle as much as they can. Every step clears at least one person,
 * so a group of n people never needs more than n - 1 payments. Finding the absolute minimum is
 * NP-hard in general, which is why real apps use a greedy approach like this one.
 */
public final class SettlementCalculator {

    private SettlementCalculator() {
    }

    public static List<Transfer> settle(Map<Long, BigDecimal> balances) {
        PriorityQueue<Position> creditors = new PriorityQueue<>(Position.LARGEST_FIRST);
        PriorityQueue<Position> debtors = new PriorityQueue<>(Position.LARGEST_FIRST);
        long sum = 0;

        for (Map.Entry<Long, BigDecimal> entry : balances.entrySet()) {
            long cents = BalanceCalculator.Money.toCents(entry.getValue());
            sum += cents;
            if (cents > 0) {
                creditors.add(new Position(entry.getKey(), cents));
            } else if (cents < 0) {
                debtors.add(new Position(entry.getKey(), -cents));
            }
        }
        if (sum != 0) {
            throw new IllegalArgumentException("Balances must add up to zero, but add up to " + sum + " cents");
        }

        List<Transfer> transfers = new ArrayList<>();
        while (!debtors.isEmpty()) {
            Position debtor = debtors.poll();
            Position creditor = creditors.poll();
            long amount = Math.min(debtor.cents(), creditor.cents());

            transfers.add(new Transfer(debtor.memberId(), creditor.memberId(),
                    BalanceCalculator.Money.fromCents(amount)));

            if (debtor.cents() > amount) {
                debtors.add(new Position(debtor.memberId(), debtor.cents() - amount));
            }
            if (creditor.cents() > amount) {
                creditors.add(new Position(creditor.memberId(), creditor.cents() - amount));
            }
        }
        return transfers;
    }

    private record Position(long memberId, long cents) {
        static final Comparator<Position> LARGEST_FIRST = Comparator
                .comparingLong(Position::cents).reversed()
                .thenComparingLong(Position::memberId);
    }
}
