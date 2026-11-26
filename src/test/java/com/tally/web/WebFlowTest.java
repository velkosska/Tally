package com.tally.web;

import com.tally.domain.Category;
import com.tally.service.ExpenseCommand;
import com.tally.service.ExpenseService;
import com.tally.service.GroupCommand;
import com.tally.service.GroupService;
import com.tally.service.DashboardService;
import com.tally.service.view.MemberOption;
import com.tally.settlement.SplitMethod;
import com.tally.security.TallyUser;
import com.tally.security.TallyUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import com.tally.TestcontainersConfiguration;
import com.tally.account.AccountService;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Drives the app through HTTP and renders the real Thymeleaf templates,
 * so a broken template fails the build instead of failing in front of a user.
 * Requests run as a logged-in user; see {@code SecurityFlowTest} for the login itself.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WebFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    GroupService groupService;
    @Autowired
    ExpenseService expenseService;
    @Autowired
    DashboardService dashboardService;
    @Autowired
    AccountService accountService;
    @Autowired
    TallyUserDetailsService userDetailsService;

    TallyUser ana;

    @BeforeEach
    void logInAsAna() {
        accountService.signUp("ana@example.com", "Ana", "correct horse battery");
        ana = userDetailsService.loadUserByUsername("ana@example.com");
    }

    @Test
    void homePageRendersEmptyState() throws Exception {
        mvc.perform(get("/").with(user(ana)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No groups yet")));
    }

    @Test
    void creatingAGroupRedirectsToIt() throws Exception {
        mvc.perform(post("/groups").with(user(ana)).with(csrf())
                        .param("name", "Road trip").param("currency", "EUR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/groups/*"));
    }

    @Test
    void blankGroupNameShowsAValidationError() throws Exception {
        mvc.perform(post("/groups").with(user(ana)).with(csrf())
                        .param("name", " ").param("currency", "EUR"))
                .andExpect(status().isOk())
                .andExpect(view().name("groups/form"))
                .andExpect(model().attributeHasFieldErrors("groupForm", "name"))
                .andExpect(content().string(containsString("Give your group a name.")));
    }

    @Test
    void groupPageShowsBalancesAndSettlePlan() throws Exception {
        long groupId = groupWithExpense();

        mvc.perform(get("/groups/{id}", groupId).with(user(ana)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Settle up")))
                .andExpect(content().string(containsString("€45.00")))
                .andExpect(content().string(containsString("1 payment settles everything.")));
    }

    @Test
    void expenseFormsRender() throws Exception {
        long groupId = groupWithExpense();
        long expenseId = dashboardService.dashboard(ana.id(), groupId).expenses().getFirst().id();

        mvc.perform(get("/groups/{id}/expenses/new", groupId).with(user(ana)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("How to split it")));
        mvc.perform(get("/groups/{g}/expenses/{e}/edit", groupId, expenseId).with(user(ana)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hotel")));
    }

    @Test
    void invalidExpenseIsRejectedWithMessages() throws Exception {
        long groupId = groupWithExpense();

        mvc.perform(post("/groups/{id}/expenses", groupId).with(user(ana)).with(csrf())
                        .param("description", "").param("amount", "-3"))
                .andExpect(status().isOk())
                .andExpect(view().name("expenses/form"))
                .andExpect(model().attributeHasFieldErrors("expenseForm", "description", "amount", "participantIds"));
    }

    @Test
    void csvExportDownloadsAFile() throws Exception {
        long groupId = groupWithExpense();

        mvc.perform(get("/groups/{id}/export.csv", groupId).with(user(ana)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("Hotel")));
    }

    @Test
    void unknownGroupShowsFriendly404() throws Exception {
        mvc.perform(get("/groups/{id}", 987654).with(user(ana)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("That page doesn’t exist")));
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UP")));
    }

    @Test
    void anExpenseCanBeSplitByShares() throws Exception {
        long groupId = groupWithExpense();
        List<MemberOption> members = dashboardService.dashboard(ana.id(), groupId).members();
        long anaMember = members.get(0).id();
        long benMember = members.get(1).id();

        mvc.perform(post("/groups/{g}/expenses", groupId).with(user(ana)).with(csrf())
                        .param("description", "Cabin").param("amount", "300.00").param("category", "ACCOMMODATION")
                        .param("date", LocalDate.now().toString()).param("paidById", String.valueOf(benMember))
                        .param("participantIds", String.valueOf(anaMember), String.valueOf(benMember))
                        .param("splitMethod", "SHARES")
                        .param("weights[" + anaMember + "]", "2")
                        .param("weights[" + benMember + "]", "1"))
                .andExpect(status().is3xxRedirection());

        var cabin = dashboardService.dashboard(ana.id(), groupId).expenses().stream()
                .filter(e -> e.description().equals("Cabin")).findFirst().orElseThrow();
        assertThat(cabin.shares()).extracting("name", "amount")
                .containsExactly(tuple("Ana", new BigDecimal("200.00")), tuple("Ben", new BigDecimal("100.00")));
        mvc.perform(get("/groups/{id}", groupId).with(user(ana)))
                .andExpect(content().string(containsString("Ben paid, split by shares")))
                .andExpect(content().string(containsString("Ana €200.00")));
        mvc.perform(get("/groups/{g}/expenses/{e}/edit", groupId, cabin.id()).with(user(ana)))
                .andExpect(model().attribute("expenseForm", hasProperty("splitMethod", is(SplitMethod.SHARES))));
    }

    @Test
    void percentagesThatDoNotAddUpAreRejected() throws Exception {
        long groupId = groupWithExpense();
        List<MemberOption> members = dashboardService.dashboard(ana.id(), groupId).members();

        mvc.perform(post("/groups/{g}/expenses", groupId).with(user(ana)).with(csrf())
                        .param("description", "Tickets").param("amount", "50").param("category", "OTHER")
                        .param("date", LocalDate.now().toString())
                        .param("paidById", String.valueOf(members.get(0).id()))
                        .param("participantIds", String.valueOf(members.get(0).id()), String.valueOf(members.get(1).id()))
                        .param("splitMethod", "PERCENT")
                        .param("weights[" + members.get(0).id() + "]", "60")
                        .param("weights[" + members.get(1).id() + "]", "30"))
                .andExpect(status().isOk())
                .andExpect(view().name("expenses/form"))
                .andExpect(content().string(containsString("The percentages add up to 90%")));
        assertThat(dashboardService.dashboard(ana.id(), groupId).expenses()).hasSize(1);
    }

    private long groupWithExpense() {
        long groupId = groupService.create(ana.id(), new GroupCommand("Weekend", null, "EUR"));
        groupService.addMember(ana.id(), groupId, "Ben");
        List<Long> ids = dashboardService.dashboard(ana.id(), groupId).members().stream()
                .map(MemberOption::id).toList();
        expenseService.create(ana.id(), groupId, new ExpenseCommand("Hotel", new BigDecimal("90.00"),
                Category.ACCOMMODATION, LocalDate.now(), ids.getFirst(), ids));
        return groupId;
    }
}
