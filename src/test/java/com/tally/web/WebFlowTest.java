package com.tally.web;

import com.tally.domain.Category;
import com.tally.service.ExpenseCommand;
import com.tally.service.ExpenseService;
import com.tally.service.GroupCommand;
import com.tally.service.GroupService;
import com.tally.service.DashboardService;
import com.tally.service.view.MemberOption;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
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
 */
@SpringBootTest
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

    @Test
    void homePageRendersEmptyState() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No groups yet")));
    }

    @Test
    void creatingAGroupRedirectsToIt() throws Exception {
        mvc.perform(post("/groups").param("name", "Road trip").param("currency", "EUR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/groups/*"));
    }

    @Test
    void blankGroupNameShowsAValidationError() throws Exception {
        mvc.perform(post("/groups").param("name", " ").param("currency", "EUR"))
                .andExpect(status().isOk())
                .andExpect(view().name("groups/form"))
                .andExpect(model().attributeHasFieldErrors("groupForm", "name"))
                .andExpect(content().string(containsString("Give your group a name.")));
    }

    @Test
    void groupPageShowsBalancesAndSettlePlan() throws Exception {
        long groupId = groupWithExpense();

        mvc.perform(get("/groups/{id}", groupId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Settle up")))
                .andExpect(content().string(containsString("€45.00")))
                .andExpect(content().string(containsString("1 payment settles everything.")));
    }

    @Test
    void expenseFormsRender() throws Exception {
        long groupId = groupWithExpense();
        long expenseId = dashboardService.dashboard(groupId).expenses().getFirst().id();

        mvc.perform(get("/groups/{id}/expenses/new", groupId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Split equally between")));
        mvc.perform(get("/groups/{g}/expenses/{e}/edit", groupId, expenseId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hotel")));
    }

    @Test
    void invalidExpenseIsRejectedWithMessages() throws Exception {
        long groupId = groupWithExpense();

        mvc.perform(post("/groups/{id}/expenses", groupId).param("description", "").param("amount", "-3"))
                .andExpect(status().isOk())
                .andExpect(view().name("expenses/form"))
                .andExpect(model().attributeHasFieldErrors("expenseForm", "description", "amount", "participantIds"));
    }

    @Test
    void csvExportDownloadsAFile() throws Exception {
        long groupId = groupWithExpense();

        mvc.perform(get("/groups/{id}/export.csv", groupId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("Hotel")));
    }

    @Test
    void unknownGroupShowsFriendly404() throws Exception {
        mvc.perform(get("/groups/{id}", 987654))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("That page doesn’t exist")));
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UP")));
    }

    private long groupWithExpense() {
        long groupId = groupService.create(new GroupCommand("Weekend", null, "EUR"));
        groupService.addMember(groupId, "Ana");
        groupService.addMember(groupId, "Ben");
        List<Long> ids = dashboardService.dashboard(groupId).members().stream().map(MemberOption::id).toList();
        expenseService.create(groupId, new ExpenseCommand("Hotel", new BigDecimal("90.00"),
                Category.ACCOMMODATION, LocalDate.now(), ids.getFirst(), ids));
        return groupId;
    }
}
