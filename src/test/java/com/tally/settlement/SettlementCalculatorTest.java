package com.tally.settlement;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementCalculatorTest {

    @Test
    void everyoneSquareMeansNoPayments() {
        assertThat(SettlementCalculator.settle(Map.of(1L, BigDecimal.ZERO, 2L, BigDecimal.ZERO))).isEmpty();
    }

    @Test
    void oneDebtorPaysOneCreditor() {
        var transfers = SettlementCalculator.settle(balances(1, "25.00", 2, "-25.00"));

        assertThat(transfers).containsExactly(new Transfer(2, 1, new BigDecimal("25.00")));
    }

    @Test
    void largestDebtIsMatchedWithLargestCredit() {
        // A is owed 60, B is owed 10; C owes 50, D owes 20
        var transfers = SettlementCalculator.settle(balances(1, "60", 2, "10", 3, "-50", 4, "-20"));

        assertThat(transfers).containsExactly(
                new Transfer(3, 1, new BigDecimal("50.00")),
                new Transfer(4, 1, new BigDecimal("10.00")),
                new Transfer(4, 2, new BigDecimal("10.00")));
    }

    @Test
    void rejectsBalancesThatDoNotAddUp() {
        assertThatThrownBy(() -> SettlementCalculator.settle(balances(1, "10", 2, "-5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Property test: for random groups, the plan settles everyone and needs at most n - 1 payments. */
    @RepeatedTest(200)
    void randomGroupsAreAlwaysSettledInAtMostNMinusOnePayments() {
        Random random = new Random();
        int people = 2 + random.nextInt(8);
        List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= people; id++) {
            ids.add(id);
        }
        List<ExpenseShare> expenses = new ArrayList<>();
        for (int i = 0; i < 1 + random.nextInt(15); i++) {
            List<Long> participants = ids.stream().filter(id -> random.nextBoolean()).toList();
            expenses.add(new ExpenseShare(ids.get(random.nextInt(people)),
                    BigDecimal.valueOf(1 + random.nextInt(99_999), 2),
                    participants.isEmpty() ? List.of(ids.getFirst()) : participants));
        }

        Map<Long, BigDecimal> net = BalanceCalculator.netBalances(ids, expenses);
        List<Transfer> transfers = SettlementCalculator.settle(net);

        Map<Long, BigDecimal> after = new HashMap<>(net);
        for (Transfer t : transfers) {
            assertThat(t.amount()).isPositive();
            after.merge(t.fromId(), t.amount(), BigDecimal::add);
            after.merge(t.toId(), t.amount().negate(), BigDecimal::add);
        }
        assertThat(after.values()).allSatisfy(balance -> assertThat(balance).isZero());
        assertThat(transfers).hasSizeLessThanOrEqualTo(people - 1);
    }

    private static Map<Long, BigDecimal> balances(Object... idsAndAmounts) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (int i = 0; i < idsAndAmounts.length; i += 2) {
            map.put(((Integer) idsAndAmounts[i]).longValue(), new BigDecimal((String) idsAndAmounts[i + 1]));
        }
        return map;
    }
}
