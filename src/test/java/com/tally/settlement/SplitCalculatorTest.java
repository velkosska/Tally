package com.tally.settlement;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitCalculatorTest {

    @Test
    void equalSplitsGiveLeftoverCentsToTheLowestIds() {
        var shares = SplitCalculator.split(1001, SplitMethod.EQUAL, weights(3L, "1", 1L, "1", 2L, "1"));

        assertThat(shares).containsExactly(Map.entry(1L, 334L), Map.entry(2L, 334L), Map.entry(3L, 333L));
    }

    @Test
    void equalSplitsIgnoreTheNumbers() {
        var shares = SplitCalculator.split(1000, SplitMethod.EQUAL, weights(1L, "7", 2L, "0"));

        assertThat(shares).containsEntry(1L, 500L).containsEntry(2L, 500L);
    }

    @Test
    void exactAmountsAreUsedAsGiven() {
        var shares = SplitCalculator.split(5000, SplitMethod.EXACT, weights(1L, "12.50", 2L, "37.5"));

        assertThat(shares).containsEntry(1L, 1250L).containsEntry(2L, 3750L);
    }

    @Test
    void exactAmountsMustAddUpToTheTotal() {
        assertThatThrownBy(() -> SplitCalculator.split(5000, SplitMethod.EXACT, weights(1L, "10", 2L, "30")))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("The amounts add up to 40.00, 10.00 short of the total.");
        assertThatThrownBy(() -> SplitCalculator.split(5000, SplitMethod.EXACT, weights(1L, "30", 2L, "30")))
                .hasMessage("The amounts add up to 60.00, 10.00 more than the total.");
        assertThatThrownBy(() -> SplitCalculator.split(5000, SplitMethod.EXACT, weights(1L, "25.005", 2L, "24.995")))
                .hasMessage("Use at most two decimals for each amount.");
    }

    @Test
    void percentagesMustAddUpTo100() {
        var shares = SplitCalculator.split(1000, SplitMethod.PERCENT, weights(1L, "33.33", 2L, "33.33", 3L, "33.34"));
        assertThat(shares).containsEntry(1L, 333L).containsEntry(2L, 333L).containsEntry(3L, 334L);

        assertThatThrownBy(() -> SplitCalculator.split(1000, SplitMethod.PERCENT, weights(1L, "50", 2L, "40")))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("The percentages add up to 90%. They need to add up to 100%.");
    }

    @Test
    void sharesAreProportionalAndLeftoverCentsGoToTheLargestRemainder() {
        // 100.00 split 1 : 2 : 3 → 16.666…, 33.333…, 50.00 → the cent left over goes to id 1 (remainder .67)
        var shares = SplitCalculator.split(10000, SplitMethod.SHARES, weights(1L, "1", 2L, "2", 3L, "3"));

        assertThat(shares).containsEntry(1L, 1667L).containsEntry(2L, 3333L).containsEntry(3L, 5000L);
    }

    @Test
    void halfSharesWork() {
        var shares = SplitCalculator.split(3000, SplitMethod.SHARES, weights(1L, "1.5", 2L, "0.5", 3L, "1"));

        assertThat(shares).containsEntry(1L, 1500L).containsEntry(2L, 500L).containsEntry(3L, 1000L);
    }

    @Test
    void everyoneInAnUnevenSplitNeedsANumberAboveZero() {
        assertThatThrownBy(() -> SplitCalculator.split(1000, SplitMethod.SHARES, weights(1L, "1", 2L, "0")))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("Enter a number of shares above zero");
        Map<Long, BigDecimal> missing = new LinkedHashMap<>();
        missing.put(1L, new BigDecimal("10.00"));
        missing.put(2L, null);
        assertThatThrownBy(() -> SplitCalculator.split(1000, SplitMethod.EXACT, missing))
                .hasMessageContaining("Enter an amount above zero");
        assertThatThrownBy(() -> SplitCalculator.split(1000, SplitMethod.EQUAL, Map.of()))
                .hasMessageContaining("Pick at least one person");
    }

    /** Whatever the numbers, the shares add up to the total and nobody is off by more than a cent. */
    @RepeatedTest(200)
    void sharesAlwaysAddUpToTheTotal() {
        Random random = new Random();
        long total = 1 + random.nextInt(1_000_000);
        Map<Long, BigDecimal> weights = new LinkedHashMap<>();
        int people = 1 + random.nextInt(8);
        for (long id = 1; id <= people; id++) {
            weights.put(id, BigDecimal.valueOf(1 + random.nextInt(1000), random.nextInt(3)));
        }
        BigDecimal weightSum = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        var shares = SplitCalculator.split(total, SplitMethod.SHARES, weights);

        assertThat(shares.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(total);
        shares.forEach((id, cents) -> {
            double exact = total * weights.get(id).doubleValue() / weightSum.doubleValue();
            assertThat(Math.abs(cents - exact)).isLessThan(1.0);
        });
    }

    private static Map<Long, BigDecimal> weights(Object... idsAndValues) {
        Map<Long, BigDecimal> weights = new LinkedHashMap<>();
        for (int i = 0; i < idsAndValues.length; i += 2) {
            weights.put((Long) idsAndValues[i], new BigDecimal((String) idsAndValues[i + 1]));
        }
        return weights;
    }
}
