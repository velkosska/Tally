package com.tally.web;

import com.tally.TestcontainersConfiguration;
import com.tally.account.AccountService;
import com.tally.domain.BusinessRuleException;
import com.tally.domain.Category;
import com.tally.security.TallyUser;
import com.tally.security.TallyUserDetailsService;
import com.tally.service.DashboardService;
import com.tally.service.ExpenseCommand;
import com.tally.service.ExpenseService;
import com.tally.service.GroupCommand;
import com.tally.service.GroupService;
import com.tally.service.RepaymentCommand;
import com.tally.service.RepaymentService;
import com.tally.service.view.GroupDashboard;
import com.tally.service.view.MemberBalance;
import com.tally.service.view.Payment;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recording payments between members: balances, the settle-up plan, "Mark as paid", deleting and rules. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RepaymentFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    AccountService accountService;
    @Autowired
    TallyUserDetailsService userDetailsService;
    @Autowired
    GroupService groupService;
    @Autowired
    ExpenseService expenseService;
    @Autowired
    RepaymentService repaymentService;
    @Autowired
    DashboardService dashboardService;
    @Autowired
    EntityManager entityManager;

    long anaId;
    TallyUser ana;
    long groupId;
    long anaMember;
    long benMember;
    long carlaMember;

    /** Ana paid 90 for a dinner split three ways, so Ben and Carla owe her 30 each. */
    @BeforeEach
    void dinnerPaidByAna() {
        anaId = accountService.signUp("ana@example.com", "Ana", "correct horse battery");
        ana = userDetailsService.loadUserByUsername("ana@example.com");
        groupId = groupService.create(anaId, new GroupCommand("Dinner club", null, "EUR"));
        anaMember = dashboardService.dashboard(anaId, groupId).members().getFirst().id();
        benMember = groupService.addMember(anaId, groupId, "Ben");
        carlaMember = groupService.addMember(anaId, groupId, "Carla");
        expenseService.create(anaId, groupId, new ExpenseCommand("Dinner", new BigDecimal("90.00"), Category.FOOD,
                LocalDate.now(), anaMember, List.of(anaMember, benMember, carlaMember)));
    }

    @Test
    void aPaymentMovesBothBalancesAndShrinksThePlan() {
        repaymentService.record(anaId, groupId, new RepaymentCommand(benMember, anaMember, new BigDecimal("30.00"),
                LocalDate.now(), "Bank transfer"));
        reloadFromDatabase();

        GroupDashboard dashboard = dashboardService.dashboard(anaId, groupId);
        assertThat(dashboard.balances())
                .extracting(MemberBalance::name, b -> b.net().toPlainString(), b -> b.repaid().toPlainString())
                .containsExactly(tuple("Ana", "30.00", "-30.00"), tuple("Ben", "0.00", "30.00"),
                        tuple("Carla", "-30.00", "0.00"));
        assertThat(dashboard.balances()).filteredOn(b -> b.name().equals("Ben"))
                .extracting(b -> b.share().toPlainString()).containsExactly("30.00");
        assertThat(dashboard.settlement()).extracting(Payment::fromName, Payment::toName)
                .containsExactly(tuple("Carla", "Ana"));
        assertThat(dashboard.repayments()).extracting("fromName", "toName", "note")
                .containsExactly(tuple("Ben", "Ana", "Bank transfer"));
    }

    @Test
    void payingEverythingBackSettlesTheGroup() {
        repaymentService.record(anaId, groupId, new RepaymentCommand(benMember, anaMember, new BigDecimal("30.00"),
                LocalDate.now(), null));
        repaymentService.record(anaId, groupId, new RepaymentCommand(carlaMember, anaMember, new BigDecimal("30.00"),
                LocalDate.now(), " "));

        GroupDashboard dashboard = dashboardService.dashboard(anaId, groupId);
        assertThat(dashboard.isSettled()).isTrue();
        assertThat(dashboard.repayments()).extracting("note").containsOnlyNulls();
    }

    @Test
    void deletingAPaymentPutsTheBalanceBack() {
        long id = repaymentService.record(anaId, groupId, new RepaymentCommand(benMember, anaMember,
                new BigDecimal("30.00"), LocalDate.now(), null));

        repaymentService.delete(anaId, groupId, id);

        assertThat(dashboardService.dashboard(anaId, groupId).settlement()).hasSize(2);
    }

    @Test
    void nobodyCanPayThemselvesAndPeopleWithPaymentsCanNotBeRemoved() {
        assertThatThrownBy(() -> repaymentService.record(anaId, groupId, new RepaymentCommand(benMember, benMember,
                new BigDecimal("5.00"), LocalDate.now(), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("two different people");

        long dani = groupService.addMember(anaId, groupId, "Dani");
        repaymentService.record(anaId, groupId, new RepaymentCommand(dani, anaMember, new BigDecimal("5.00"),
                LocalDate.now(), null));
        assertThatThrownBy(() -> groupService.removeMember(anaId, groupId, dani))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("recorded payments");
    }

    @Test
    void deletingTheGroupDeletesItsPayments() {
        repaymentService.record(anaId, groupId, new RepaymentCommand(benMember, anaMember, new BigDecimal("30.00"),
                LocalDate.now(), null));
        reloadFromDatabase();

        groupService.delete(anaId, groupId);
        reloadFromDatabase();

        assertThat(groupService.listGroups(anaId)).isEmpty();
    }

    @Test
    void markAsPaidOnTheSettleUpPlanRecordsThePayment() throws Exception {
        mvc.perform(get("/groups/{g}", groupId).with(user(ana)))
                .andExpect(content().string(containsString("Mark as paid")))
                .andExpect(content().string(containsString("Payments made")));

        mvc.perform(post("/groups/{g}/repayments", groupId).with(user(ana)).with(csrf())
                        .param("fromMemberId", String.valueOf(benMember))
                        .param("toMemberId", String.valueOf(anaMember))
                        .param("amount", "30.00"))
                .andExpect(redirectedUrl("/groups/" + groupId + "#payments"))
                .andExpect(flash().attribute("success", "Payment recorded. Balances are updated."));

        mvc.perform(get("/groups/{g}", groupId).with(user(ana)))
                .andExpect(content().string(containsString("Ben paid Ana")))
                .andExpect(content().string(containsString("got back €30.00")));
        mvc.perform(get("/groups/{g}/export.csv", groupId).with(user(ana)))
                .andExpect(content().string(containsString("Payments made")));
    }

    @Test
    void badPaymentsShowAMessageAndChangeNothing() throws Exception {
        mvc.perform(post("/groups/{g}/repayments", groupId).with(user(ana)).with(csrf())
                        .param("fromMemberId", String.valueOf(benMember))
                        .param("toMemberId", String.valueOf(anaMember))
                        .param("amount", "-5"))
                .andExpect(flash().attribute("error", "The amount must be at least 0.01."));
        mvc.perform(post("/groups/{g}/repayments", groupId).with(user(ana)).with(csrf())
                        .param("fromMemberId", String.valueOf(benMember))
                        .param("toMemberId", String.valueOf(benMember))
                        .param("amount", "5"))
                .andExpect(flash().attribute("error",
                        "Pick two different people: someone can't pay themselves back."));

        assertThat(dashboardService.dashboard(anaId, groupId).repayments()).isEmpty();
    }

    @Test
    void outsidersCanNotRecordOrDeletePayments() throws Exception {
        long id = repaymentService.record(anaId, groupId, new RepaymentCommand(benMember, anaMember,
                new BigDecimal("30.00"), LocalDate.now(), null));
        accountService.signUp("mallory@example.com", "Mallory", "not invited at all");
        TallyUser mallory = userDetailsService.loadUserByUsername("mallory@example.com");

        mvc.perform(post("/groups/{g}/repayments", groupId).with(user(mallory)).with(csrf())
                        .param("fromMemberId", String.valueOf(benMember))
                        .param("toMemberId", String.valueOf(anaMember)).param("amount", "1"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/groups/{g}/repayments/{r}/delete", groupId, id).with(user(mallory)).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(dashboardService.dashboard(anaId, groupId).repayments()).hasSize(1);
    }

    private void reloadFromDatabase() {
        entityManager.flush();
        entityManager.clear();
    }
}
