package com.tally.service;

import com.tally.domain.BusinessRuleException;
import com.tally.domain.Category;
import com.tally.domain.ExpenseGroupRepository;
import com.tally.domain.NotFoundException;
import com.tally.service.view.GroupDashboard;
import com.tally.service.view.MemberOption;
import com.tally.service.view.Payment;
import com.tally.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the real services against the real schema (Flyway on PostgreSQL via Testcontainers) to test whole use cases. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class ExpenseFlowIntegrationTest {

    @Autowired
    GroupService groupService;
    @Autowired
    ExpenseService expenseService;
    @Autowired
    DashboardService dashboardService;
    @Autowired
    ExpenseGroupRepository groups;

    @Test
    void expensesProduceBalancesAndASettlePlan() {
        long groupId = groupService.create(new GroupCommand("Ski trip", null, "EUR"));
        groupService.addMember(groupId, "Ana");
        groupService.addMember(groupId, "Ben");
        groupService.addMember(groupId, "Carla");
        List<Long> ids = memberIds(groupId);

        expenseService.create(groupId, expense("Chalet", "300.00", ids.get(0), ids));
        expenseService.create(groupId, expense("Fuel", "60.00", ids.get(1), ids));

        GroupDashboard dashboard = dashboardService.dashboard(groupId);
        assertThat(dashboard.totalSpent()).isEqualByComparingTo("360.00");
        assertThat(dashboard.averagePerPerson()).isEqualByComparingTo("120.00");
        assertThat(dashboard.settlement()).containsExactlyInAnyOrder(
                new Payment("Carla", "Ana", new BigDecimal("120.00")),
                new Payment("Ben", "Ana", new BigDecimal("60.00")));
    }

    @Test
    void memberNamesMustBeUniqueIgnoringCase() {
        long groupId = groupService.create(new GroupCommand("Flat", null, "EUR"));
        groupService.addMember(groupId, "Maya");

        assertThatThrownBy(() -> groupService.addMember(groupId, " maya "))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void memberOnAnExpenseCannotBeRemoved() {
        long groupId = groupService.create(new GroupCommand("Flat", null, "EUR"));
        groupService.addMember(groupId, "Maya");
        long maya = memberIds(groupId).getFirst();
        expenseService.create(groupId, expense("Rent", "800.00", maya, List.of(maya)));

        assertThatThrownBy(() -> groupService.removeMember(groupId, maya))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void editingAnExpenseUpdatesTheTotals() {
        long groupId = groupService.create(new GroupCommand("Dinner", null, "GBP"));
        groupService.addMember(groupId, "Leo");
        long leo = memberIds(groupId).getFirst();
        expenseService.create(groupId, expense("Pizza", "20.00", leo, List.of(leo)));
        long expenseId = dashboardService.dashboard(groupId).expenses().getFirst().id();

        expenseService.update(groupId, expenseId, expense("Pizza and drinks", "32.50", leo, List.of(leo)));

        assertThat(dashboardService.dashboard(groupId).totalSpent()).isEqualByComparingTo("32.50");
    }

    @Test
    void deletingAGroupRemovesItsExpenses() {
        long groupId = groupService.create(new GroupCommand("Temporary", null, "EUR"));
        groupService.addMember(groupId, "Sam");
        long sam = memberIds(groupId).getFirst();
        expenseService.create(groupId, expense("Taxi", "15.00", sam, List.of(sam)));

        groupService.delete(groupId);
        groups.flush();

        assertThatThrownBy(() -> dashboardService.dashboard(groupId)).isInstanceOf(NotFoundException.class);
    }

    private List<Long> memberIds(long groupId) {
        return dashboardService.dashboard(groupId).members().stream().map(MemberOption::id).toList();
    }

    private static ExpenseCommand expense(String what, String amount, long paidBy, List<Long> participants) {
        return new ExpenseCommand(what, new BigDecimal(amount), Category.OTHER, LocalDate.now(), paidBy, participants);
    }
}
