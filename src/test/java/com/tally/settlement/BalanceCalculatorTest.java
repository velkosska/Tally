package com.tally.settlement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceCalculatorTest {

    @Test
    void payerGetsBackWhatOthersOwe() {
        var balances = BalanceCalculator.netBalances(List.of(1L, 2L),
                List.of(new ExpenseShare(1, new BigDecimal("50.00"), List.of(1L, 2L))));

        assertThat(balances).containsExactly(
                Map.entry(1L, new BigDecimal("25.00")),
                Map.entry(2L, new BigDecimal("-25.00")));
    }

    @Test
    void unevenSplitNeverLosesACent() {
        var balances = BalanceCalculator.netBalances(List.of(1L, 2L, 3L),
                List.of(new ExpenseShare(1, new BigDecimal("100.00"), List.of(1L, 2L, 3L))));

        // 100.00 / 3 = 33.34 + 33.33 + 33.33; the extra cent goes to the lowest id
        assertThat(balances.get(1L)).isEqualByComparingTo("66.66");
        assertThat(balances.get(2L)).isEqualByComparingTo("-33.33");
        assertThat(balances.get(3L)).isEqualByComparingTo("-33.33");
        assertThat(sum(balances)).isZero();
    }

    @Test
    void memberWithoutExpensesIsAtZero() {
        var balances = BalanceCalculator.netBalances(List.of(1L, 2L, 3L),
                List.of(new ExpenseShare(1, new BigDecimal("10.00"), List.of(1L, 2L))));

        assertThat(balances.get(3L)).isEqualByComparingTo("0");
    }

    @Test
    void payerWhoIsNotAParticipantGetsEverythingBack() {
        var balances = BalanceCalculator.netBalances(List.of(1L, 2L),
                List.of(new ExpenseShare(1, new BigDecimal("30.00"), List.of(2L))));

        assertThat(balances.get(1L)).isEqualByComparingTo("30.00");
        assertThat(balances.get(2L)).isEqualByComparingTo("-30.00");
    }

    @Test
    void splitEvenlyAlwaysAddsUpToTheTotal() {
        var shares = BalanceCalculator.splitEvenly(1001, List.of(3L, 1L, 2L));

        assertThat(shares).containsEntry(1L, 334L).containsEntry(2L, 334L).containsEntry(3L, 333L);
        assertThat(shares.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(1001);
    }

    private static BigDecimal sum(Map<Long, BigDecimal> balances) {
        return balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
