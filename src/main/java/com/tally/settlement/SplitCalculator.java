package com.tally.settlement;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Divides an expense, in whole cents, between its participants. The shares always add up to the
 * total exactly, so balances never drift by a cent.
 *
 * <ul>
 *   <li>{@link SplitMethod#EQUAL}: the same for everyone; leftover cents go to the lowest ids.</li>
 *   <li>{@link SplitMethod#EXACT}: the amounts given, which must add up to the total.</li>
 *   <li>{@link SplitMethod#PERCENT} and {@link SplitMethod#SHARES}: proportional. Each person first gets
 *       their rounded-down share, then the leftover cents go one each to the people whose share was
 *       rounded down the most (the "largest remainder" method), lowest id first on a tie.</li>
 * </ul>
 */
public final class SplitCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private SplitCalculator() {
    }

    /**
     * @param totalCents the expense amount in cents, positive
     * @param weights    participant id to their number (ignored for EQUAL): an amount, a percentage or shares
     * @return participant id to their share in cents, ordered by id
     * @throws InvalidSplitException with a message that can be shown to the user
     */
    public static Map<Long, Long> split(long totalCents, SplitMethod method, Map<Long, BigDecimal> weights) {
        if (weights.isEmpty()) {
            throw new InvalidSplitException("Pick at least one person to split the expense between.");
        }
        Map<Long, BigDecimal> ordered = new TreeMap<>(weights);
        return switch (method) {
            case EQUAL -> equal(totalCents, ordered);
            case EXACT -> exact(totalCents, positive(ordered, "an amount"));
            case PERCENT -> percent(totalCents, positive(ordered, "a percentage"));
            case SHARES -> proportional(totalCents, positive(ordered, "a number of shares"));
        };
    }

    private static Map<Long, Long> equal(long totalCents, Map<Long, BigDecimal> ordered) {
        Map<Long, BigDecimal> ones = new TreeMap<>();
        ordered.keySet().forEach(id -> ones.put(id, BigDecimal.ONE));
        return proportional(totalCents, ones);
    }

    private static Map<Long, Long> exact(long totalCents, Map<Long, BigDecimal> amounts) {
        Map<Long, Long> shares = new LinkedHashMap<>();
        long sum = 0;
        for (var entry : amounts.entrySet()) {
            if (entry.getValue().stripTrailingZeros().scale() > 2) {
                throw new InvalidSplitException("Use at most two decimals for each amount.");
            }
            long cents = entry.getValue().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            shares.put(entry.getKey(), cents);
            sum += cents;
        }
        if (sum != totalCents) {
            BigDecimal difference = BigDecimal.valueOf(Math.abs(totalCents - sum), 2);
            throw new InvalidSplitException(sum < totalCents
                    ? "The amounts add up to " + BigDecimal.valueOf(sum, 2) + ", " + difference + " short of the total."
                    : "The amounts add up to " + BigDecimal.valueOf(sum, 2) + ", " + difference + " more than the total.");
        }
        return shares;
    }

    private static Map<Long, Long> percent(long totalCents, Map<Long, BigDecimal> percentages) {
        BigDecimal sum = percentages.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(HUNDRED) != 0) {
            throw new InvalidSplitException("The percentages add up to " + sum.stripTrailingZeros().toPlainString()
                    + "%. They need to add up to 100%.");
        }
        return proportional(totalCents, percentages);
    }

    /** Largest-remainder rounding: exact integer arithmetic, so the shares always add up to the total. */
    static Map<Long, Long> proportional(long totalCents, Map<Long, BigDecimal> weights) {
        int scale = weights.values().stream().mapToInt(w -> Math.max(0, w.stripTrailingZeros().scale())).max().orElse(0);
        Map<Long, BigInteger> units = new TreeMap<>();
        weights.forEach((id, w) -> units.put(id, w.movePointRight(scale).toBigIntegerExact()));
        BigInteger totalUnits = units.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger total = BigInteger.valueOf(totalCents);

        Map<Long, Long> shares = new TreeMap<>();
        List<Map.Entry<Long, BigInteger>> remainders = new ArrayList<>();
        long assigned = 0;
        for (var entry : units.entrySet()) {
            BigInteger[] division = total.multiply(entry.getValue()).divideAndRemainder(totalUnits);
            long base = division[0].longValueExact();
            shares.put(entry.getKey(), base);
            assigned += base;
            remainders.add(Map.entry(entry.getKey(), division[1]));
        }
        remainders.sort(Map.Entry.<Long, BigInteger>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));
        for (int i = 0; i < totalCents - assigned; i++) {
            shares.merge(remainders.get(i).getKey(), 1L, Long::sum);
        }
        return shares;
    }

    private static Map<Long, BigDecimal> positive(Map<Long, BigDecimal> weights, String what) {
        for (BigDecimal weight : weights.values()) {
            if (weight == null || weight.signum() <= 0) {
                throw new InvalidSplitException("Enter " + what + " above zero for everyone in the split, or untick them.");
            }
        }
        return weights;
    }
}
