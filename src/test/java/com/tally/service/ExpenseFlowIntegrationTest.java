package com.tally.service;

import com.tally.TestcontainersConfiguration;
import com.tally.account.AccountService;
import com.tally.domain.BusinessRuleException;
import com.tally.domain.Category;
import com.tally.domain.ExpenseGroupRepository;
import com.tally.domain.NotFoundException;
import com.tally.service.view.GroupDashboard;
import com.tally.service.view.MemberOption;
import com.tally.service.view.Payment;
import org.junit.jupiter.api.BeforeEach;
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
    AccountService accountService;
    @Autowired
    GroupService groupService;
    @Autowired
    ExpenseService expenseService;
    @Autowired
    DashboardService dashboardService;
    @Autowired
    ExpenseGroupRepository groups;

    /** The logged-in user in each test. Groups they create start with them as the member "Ana". */
    long ana;

    @BeforeEach
    void signUpAna() {
        ana = accountService.signUp("ana@example.com", "Ana", "correct horse battery");
    }

    @Test
    void expensesProduceBalancesAndASettlePlan() {
        long groupId = groupService.create(ana, new GroupCommand("Ski trip", null, "EUR"));
        groupService.addMember(ana, groupId, "Ben");
        groupService.addMember(ana, groupId, "Carla");
        List<Long> ids = memberIds(groupId);

        expenseService.create(ana, groupId, expense("Chalet", "300.00", memberId(groupId, "Ana"), ids));
        expenseService.create(ana, groupId, expense("Fuel", "60.00", memberId(groupId, "Ben"), ids));

        GroupDashboard dashboard = dashboardService.dashboard(ana, groupId);
        assertThat(dashboard.totalSpent()).isEqualByComparingTo("360.00");
        assertThat(dashboard.averagePerPerson()).isEqualByComparingTo("120.00");
        assertThat(dashboard.settlement()).containsExactlyInAnyOrder(
                new Payment("Carla", "Ana", new BigDecimal("120.00")),
                new Payment("Ben", "Ana", new BigDecimal("60.00")));
    }

    @Test
    void creatorBecomesTheFirstMemberAndCannotBeRemoved() {
        long groupId = groupService.create(ana, new GroupCommand("Flat", null, "EUR"));

        List<MemberOption> members = dashboardService.dashboard(ana, groupId).members();
        assertThat(members).extracting(MemberOption::name).containsExactly("Ana");
        assertThat(members.getFirst().removable()).isFalse();
        assertThatThrownBy(() -> groupService.removeMember(ana, groupId, members.getFirst().id()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void memberNamesMustBeUniqueIgnoringCase() {
        long groupId = groupService.create(ana, new GroupCommand("Flat", null, "EUR"));
        groupService.addMember(ana, groupId, "Maya");

        assertThatThrownBy(() -> groupService.addMember(ana, groupId, " maya "))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void memberOnAnExpenseCannotBeRemoved() {
        long groupId = groupService.create(ana, new GroupCommand("Flat", null, "EUR"));
        long maya = groupService.addMember(ana, groupId, "Maya");
        expenseService.create(ana, groupId, expense("Rent", "800.00", maya, List.of(maya)));

        assertThatThrownBy(() -> groupService.removeMember(ana, groupId, maya))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void editingAnExpenseUpdatesTheTotals() {
        long groupId = groupService.create(ana, new GroupCommand("Dinner", null, "GBP"));
        long me = memberId(groupId, "Ana");
        long expenseId = expenseService.create(ana, groupId, expense("Pizza", "20.00", me, List.of(me)));

        expenseService.update(ana, groupId, expenseId, expense("Pizza and drinks", "32.50", me, List.of(me)));

        assertThat(dashboardService.dashboard(ana, groupId).totalSpent()).isEqualByComparingTo("32.50");
    }

    @Test
    void deletingAGroupRemovesItsExpenses() {
        long groupId = groupService.create(ana, new GroupCommand("Temporary", null, "EUR"));
        long me = memberId(groupId, "Ana");
        expenseService.create(ana, groupId, expense("Taxi", "15.00", me, List.of(me)));

        groupService.delete(ana, groupId);
        groups.flush();

        assertThatThrownBy(() -> dashboardService.dashboard(ana, groupId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void usersOnlySeeTheirOwnGroups() {
        long anasGroup = groupService.create(ana, new GroupCommand("Ana's trip", null, "EUR"));
        long ben = accountService.signUp("ben@example.com", "Ben", "another good password");
        groupService.create(ben, new GroupCommand("Ben's flat", null, "EUR"));

        assertThat(groupService.listGroups(ana)).extracting("name").containsExactly("Ana's trip");
        assertThat(groupService.listGroups(ben)).extracting("name").containsExactly("Ben's flat");
        assertThatThrownBy(() -> dashboardService.dashboard(ben, anasGroup)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void outsidersCannotChangeAGroup() {
        long groupId = groupService.create(ana, new GroupCommand("Ana's trip", null, "EUR"));
        long me = memberId(groupId, "Ana");
        long expenseId = expenseService.create(ana, groupId, expense("Taxi", "15.00", me, List.of(me)));
        long mallory = accountService.signUp("mallory@example.com", "Mallory", "not invited at all");

        assertThatThrownBy(() -> groupService.update(mallory, groupId, new GroupCommand("Mine now", null, "EUR")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> groupService.addMember(mallory, groupId, "Mallory"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> groupService.delete(mallory, groupId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> expenseService.create(mallory, groupId, expense("Fake", "1.00", me, List.of(me))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> expenseService.update(mallory, groupId, expenseId,
                expense("Changed", "999.00", me, List.of(me)))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> expenseService.delete(mallory, groupId, expenseId))
                .isInstanceOf(NotFoundException.class);

        GroupDashboard unchanged = dashboardService.dashboard(ana, groupId);
        assertThat(unchanged.name()).isEqualTo("Ana's trip");
        assertThat(unchanged.totalSpent()).isEqualByComparingTo("15.00");
        assertThat(unchanged.members()).extracting(MemberOption::name).containsExactly("Ana");
    }

    @Test
    void emailsAreUniqueIgnoringCase() {
        assertThatThrownBy(() -> accountService.signUp(" ANA@Example.com ", "Other Ana", "some password"))
                .isInstanceOf(BusinessRuleException.class);
    }

    private List<Long> memberIds(long groupId) {
        return dashboardService.dashboard(ana, groupId).members().stream().map(MemberOption::id).toList();
    }

    private long memberId(long groupId, String name) {
        return dashboardService.dashboard(ana, groupId).members().stream()
                .filter(m -> m.name().equals(name))
                .findFirst().orElseThrow().id();
    }

    private static ExpenseCommand expense(String what, String amount, long paidBy, List<Long> participants) {
        return new ExpenseCommand(what, new BigDecimal(amount), Category.OTHER, LocalDate.now(), paidBy, participants);
    }
}
