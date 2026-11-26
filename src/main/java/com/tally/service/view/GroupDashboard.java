package com.tally.service.view;

import java.math.BigDecimal;
import java.util.List;

/** Everything the group page shows, computed in one read-only transaction. */
public record GroupDashboard(long id, String name, String description, String currency,
                             BigDecimal totalSpent, BigDecimal averagePerPerson,
                             List<MemberOption> members, List<MemberBalance> balances,
                             List<Payment> settlement, List<CategoryTotal> categories,
                             List<ExpenseRow> expenses) {

    public boolean hasExpenses() {
        return !expenses.isEmpty();
    }

    public boolean isSettled() {
        return settlement.isEmpty();
    }
}
