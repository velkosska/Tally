package com.tally.web;

import com.tally.TestcontainersConfiguration;
import com.tally.account.AccountService;
import com.tally.activity.ActivityLog;
import com.tally.activity.ActivityRow;
import com.tally.activity.ActivityType;
import com.tally.domain.Category;
import com.tally.domain.NotFoundException;
import com.tally.invite.InviteService;
import com.tally.security.TallyUser;
import com.tally.security.TallyUserDetailsService;
import com.tally.service.DashboardService;
import com.tally.service.ExpenseCommand;
import com.tally.service.ExpenseService;
import com.tally.service.GroupCommand;
import com.tally.service.GroupService;
import com.tally.service.RepaymentCommand;
import com.tally.service.RepaymentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The activity feed: what gets recorded, how it reads, who can see it, and paging. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ActivityFeedTest {

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
    InviteService inviteService;
    @Autowired
    DashboardService dashboardService;
    @Autowired
    ActivityLog activityLog;
    @Autowired
    EntityManager entityManager;
    @Autowired
    JdbcTemplate jdbc;

    long anaId;
    long benId;
    TallyUser ana;
    long groupId;
    long anaMember;

    @BeforeEach
    void anaCreatesAGroup() {
        anaId = accountService.signUp("ana@example.com", "Ana", "correct horse battery");
        benId = accountService.signUp("ben@example.com", "Ben", "another long password");
        ana = userDetailsService.loadUserByUsername("ana@example.com");
        groupId = groupService.create(anaId, new GroupCommand("Lisbon", null, "EUR"));
        anaMember = dashboardService.dashboard(anaId, groupId).members().getFirst().id();
    }

    @Test
    void everyKindOfChangeIsRecordedNewestFirst() {
        long carla = groupService.addMember(anaId, groupId, "Carla");
        long expenseId = expenseService.create(anaId, groupId, expense("Dinner", "90.00", List.of(anaMember, carla)));
        expenseService.update(anaId, groupId, expenseId, expense("Dinner at the harbour", "96.00",
                List.of(anaMember, carla)));
        long paymentId = repaymentService.record(anaId, groupId, new RepaymentCommand(carla, anaMember,
                new BigDecimal("48.00"), LocalDate.now(), "Cash"));
        repaymentService.delete(anaId, groupId, paymentId);
        expenseService.delete(anaId, groupId, expenseId);
        groupService.removeMember(anaId, groupId, carla);
        groupService.update(anaId, groupId, new GroupCommand("Lisbon trip", null, "EUR"));

        assertThat(feed(anaId)).extracting(ActivityRow::type, ActivityRow::subject).containsExactly(
                tuple(ActivityType.GROUP_UPDATED, null),
                tuple(ActivityType.MEMBER_REMOVED, "Carla"),
                tuple(ActivityType.EXPENSE_DELETED, "Dinner at the harbour"),
                tuple(ActivityType.PAYMENT_DELETED, "Carla paid Ana"),
                tuple(ActivityType.PAYMENT_RECORDED, "Carla paid Ana"),
                tuple(ActivityType.EXPENSE_UPDATED, "Dinner at the harbour"),
                tuple(ActivityType.EXPENSE_ADDED, "Dinner"),
                tuple(ActivityType.MEMBER_ADDED, "Carla"),
                tuple(ActivityType.GROUP_CREATED, null));
    }

    @Test
    void editsSayWhatChanged() {
        long carla = groupService.addMember(anaId, groupId, "Carla");
        long expenseId = expenseService.create(anaId, groupId, expense("Dinner", "90.00", List.of(anaMember, carla)));
        expenseService.update(anaId, groupId, expenseId, expense("Dinner", "96", List.of(anaMember)));

        ActivityRow edit = feed(anaId).getFirst();
        assertThat(edit.details()).isEqualTo("amount 90.00 → 96.00; now split equally between Ana");
        assertThat(edit.amount()).isEqualByComparingTo("96.00");
        assertThat(feed(anaId).get(1).details()).isEqualTo("paid by Ana, split equally between Ana, Carla");
    }

    @Test
    void joiningThroughAnInviteIsRecordedUnderTheNewMembersName() {
        String token = inviteService.createLink(anaId, groupId);
        inviteService.accept(token, benId, null, "Benny");

        List<ActivityRow> benSees = feed(benId);
        assertThat(benSees.getFirst()).extracting(ActivityRow::who, ActivityRow::type, ActivityRow::subject)
                .containsExactly("You", ActivityType.MEMBER_JOINED, "Benny");
        assertThat(benSees.get(1)).extracting(ActivityRow::who, ActivityRow::type)
                .containsExactly("Ana", ActivityType.INVITE_CREATED);
    }

    @Test
    void theFeedKeepsNamesAfterThingsAreDeleted() {
        long expenseId = expenseService.create(anaId, groupId, expense("Taxi", "15.00", List.of(anaMember)));
        expenseService.delete(anaId, groupId, expenseId);
        entityManager.flush();
        entityManager.clear();

        assertThat(feed(anaId)).extracting(ActivityRow::subject).contains("Taxi");
    }

    @Test
    void outsidersCanNotSeeAGroupsFeed() throws Exception {
        assertThatThrownBy(() -> activityLog.forGroup(benId, groupId, 0)).isInstanceOf(NotFoundException.class);
        assertThat(activityLog.latestForUser(benId, 10)).isEmpty();

        TallyUser ben = userDetailsService.loadUserByUsername("ben@example.com");
        mvc.perform(get("/groups/{g}/activity", groupId).with(user(ben))).andExpect(status().isNotFound());
        mvc.perform(get("/").with(user(ben))).andExpect(content().string(not(containsString("Lisbon"))));
    }

    @Test
    void deletingAGroupDeletesItsFeed() {
        expenseService.create(anaId, groupId, expense("Taxi", "15.00", List.of(anaMember)));

        groupService.delete(anaId, groupId);
        entityManager.flush();

        assertThat(jdbc.queryForObject("select count(*) from activity", Integer.class)).isZero();
    }

    @Test
    void pagesShowTheFeed() throws Exception {
        expenseService.create(anaId, groupId, expense("Taxi", "15.00", List.of(anaMember)));

        mvc.perform(get("/groups/{g}", groupId).with(user(ana)))
                .andExpect(content().string(containsString("Recent activity")))
                .andExpect(content().string(containsString("“Taxi”")))
                .andExpect(content().string(containsString("just now")));
        mvc.perform(get("/").with(user(ana)))
                .andExpect(content().string(containsString("Recent activity")))
                .andExpect(content().string(containsString(">Lisbon</a>")));
        mvc.perform(get("/groups/{g}/activity", groupId).with(user(ana)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("created the group")));
    }

    @Test
    void longFeedsArePaged() throws Exception {
        for (int i = 0; i < ActivityLog.PAGE_SIZE + 5; i++) {
            groupService.addMember(anaId, groupId, "Person " + i);
        }

        var first = activityLog.forGroup(anaId, groupId, 0);
        assertThat(first.getContent()).hasSize(ActivityLog.PAGE_SIZE);
        assertThat(first.getTotalPages()).isEqualTo(2);
        mvc.perform(get("/groups/{g}/activity", groupId).param("page", "1").with(user(ana)))
                .andExpect(content().string(containsString("Page 2 of 2")))
                .andExpect(content().string(containsString("created the group")));
        mvc.perform(get("/groups/{g}", groupId).with(user(ana)))
                .andExpect(content().string(containsString("See all activity")));
    }

    private List<ActivityRow> feed(long userId) {
        return activityLog.forGroup(userId, groupId, 0).getContent();
    }

    private ExpenseCommand expense(String what, String amount, List<Long> participants) {
        return new ExpenseCommand(what, new BigDecimal(amount), Category.FOOD, LocalDate.now(), anaMember,
                participants);
    }
}
