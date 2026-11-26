package com.tally.web;

import com.tally.TestcontainersConfiguration;
import com.tally.account.AccountService;
import com.tally.domain.BusinessRuleException;
import com.tally.domain.Category;
import com.tally.domain.NotFoundException;
import com.tally.invite.InvalidInviteException;
import com.tally.invite.InviteService;
import com.tally.security.TallyUser;
import com.tally.security.TallyUserDetailsService;
import com.tally.service.DashboardService;
import com.tally.service.ExpenseCommand;
import com.tally.service.ExpenseService;
import com.tally.service.GroupCommand;
import com.tally.service.GroupService;
import com.tally.service.view.Friend;
import com.tally.service.view.MemberBalance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Invite links: creating, sharing, joining, claiming a name, expiry and switching links off. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InviteFlowTest {

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
    DashboardService dashboardService;
    @Autowired
    InviteService inviteService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManager entityManager;

    long anaId;
    long benId;
    long groupId;
    long benMemberId;
    TallyUser ana;
    TallyUser ben;

    /** Ana's group has Ana (with an account) and "Ben", added by name, who paid for a dinner. */
    @BeforeEach
    void anaAddedBenByName() {
        anaId = accountService.signUp("ana@example.com", "Ana", "correct horse battery");
        benId = accountService.signUp("ben@example.com", "Benjamin", "another long password");
        ana = userDetailsService.loadUserByUsername("ana@example.com");
        ben = userDetailsService.loadUserByUsername("ben@example.com");
        groupId = groupService.create(anaId, new GroupCommand("Lisbon weekend", null, "EUR"));
        benMemberId = groupService.addMember(anaId, groupId, "Ben");
        long anaMemberId = dashboardService.dashboard(anaId, groupId).members().stream()
                .filter(m -> m.name().equals("Ana")).findFirst().orElseThrow().id();
        expenseService.create(anaId, groupId, new ExpenseCommand("Dinner", new BigDecimal("60.00"),
                Category.FOOD, LocalDate.now(), benMemberId, List.of(benMemberId, anaMemberId)));
    }

    // ---- service ----

    @Test
    void claimingANameLinksItAndKeepsItsExpenses() {
        String token = inviteService.createLink(anaId, groupId);

        var preview = inviteService.preview(token, benId);
        assertThat(preview.groupName()).isEqualTo("Lisbon weekend");
        assertThat(preview.claimable()).extracting("name").containsExactly("Ben");
        assertThat(preview.suggestedName()).isEqualTo("Benjamin");

        assertThat(inviteService.accept(token, benId, benMemberId, null)).isEqualTo(groupId);

        var dashboard = dashboardService.dashboard(benId, groupId);
        assertThat(dashboard.members()).extracting("name").containsExactly("Ana", "Ben");
        assertThat(dashboard.members()).filteredOn(m -> m.you()).extracting("name").containsExactly("Ben");
        assertThat(dashboard.balances()).filteredOn(b -> b.name().equals("Ben"))
                .extracting(MemberBalance::net).first().satisfies(net -> assertThat(net).isEqualByComparingTo("30.00"));
    }

    @Test
    void joiningAsSomeoneNewAddsAMember() {
        String token = inviteService.createLink(anaId, groupId);

        inviteService.accept(token, benId, null, "  ");   // blank name falls back to the account's name

        assertThat(dashboardService.dashboard(benId, groupId).members())
                .extracting("name").containsExactly("Ana", "Ben", "Benjamin");
        assertThat(groupService.listGroups(benId)).extracting("name").containsExactly("Lisbon weekend");
    }

    @Test
    void joiningTwiceChangesNothing() {
        String token = inviteService.createLink(anaId, groupId);
        inviteService.accept(token, benId, benMemberId, null);

        assertThat(inviteService.isMember(token, benId)).isTrue();
        inviteService.accept(token, benId, null, "Ben again");

        assertThat(dashboardService.dashboard(anaId, groupId).members()).hasSize(2);
    }

    @Test
    void aNameSomeoneAlreadyClaimedCanNotBeClaimedAgain() {
        String token = inviteService.createLink(anaId, groupId);
        inviteService.accept(token, benId, benMemberId, null);
        long carlaId = accountService.signUp("carla@example.com", "Carla", "yet another password");

        assertThatThrownBy(() -> inviteService.accept(token, carlaId, benMemberId, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already joined");
        long anaMemberId = dashboardService.dashboard(anaId, groupId).members().stream()
                .filter(m -> m.name().equals("Ana")).findFirst().orElseThrow().id();
        assertThatThrownBy(() -> inviteService.accept(token, carlaId, anaMemberId, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> inviteService.accept(token, carlaId, null, "ana"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already in this group");
    }

    @Test
    void expiredRevokedAndMadeUpLinksDoNotWork() {
        String expired = inviteService.createLink(anaId, groupId);
        entityManager.flush();
        jdbc.update("update group_invite set expires_at = dateadd(minute, -1, sysdatetimeoffset())");
        entityManager.clear();   // forget the cached invite so the next read sees the expired date
        String revoked = inviteService.createLink(anaId, groupId);
        assertThat(inviteService.countActiveLinks(anaId, groupId)).isEqualTo(1);
        inviteService.revokeAll(anaId, groupId);

        for (String token : List.of(expired, revoked, "made-up-token", "")) {
            assertThatThrownBy(() -> inviteService.accept(token, benId, null, "Ben"))
                    .isInstanceOf(InvalidInviteException.class);
        }
        assertThat(inviteService.countActiveLinks(anaId, groupId)).isZero();
        assertThat(groupService.listGroups(benId)).isEmpty();
    }

    @Test
    void onlyMembersCanCreateOrSwitchOffLinks() {
        assertThatThrownBy(() -> inviteService.createLink(benId, groupId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> inviteService.revokeAll(benId, groupId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void onlyATokenHashIsStored() {
        String token = inviteService.createLink(anaId, groupId);

        String stored = jdbc.queryForObject("select token_hash from group_invite", String.class);
        assertThat(stored).hasSize(64).isNotEqualTo(token).doesNotContain(token);
        assertThat(token).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    void friendsArePeopleWithAccountsInYourGroups() {
        assertThat(groupService.listFriends(anaId)).isEmpty();   // "Ben" is only a name so far

        inviteService.accept(inviteService.createLink(anaId, groupId), benId, benMemberId, null);
        groupService.create(benId, new GroupCommand("Flat", null, "EUR"));

        assertThat(groupService.listFriends(anaId)).containsExactly(new Friend("Benjamin", 1));
        assertThat(groupService.listFriends(benId)).containsExactly(new Friend("Ana", 1));
    }

    // ---- web ----

    @Test
    void groupPageCreatesAShareableLink() throws Exception {
        var result = mvc.perform(post("/groups/{g}/invites", groupId).with(user(ana)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups/" + groupId + "#invite"))
                .andReturn();
        String link = (String) result.getFlashMap().get("inviteLink");
        assertThat(link).startsWith("http://localhost/invites/");

        mvc.perform(get("/groups/{g}", groupId).with(user(ana)).flashAttr("inviteLink", link))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(link)))
                .andExpect(content().string(containsString("1 link works right now.")))
                .andExpect(content().string(containsString("not joined")));
    }

    @Test
    void openingALinkShowsTheGroupAndJoiningRedirectsToIt() throws Exception {
        String token = inviteService.createLink(anaId, groupId);

        mvc.perform(get("/invites/{t}", token).with(user(ben)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Join Lisbon weekend")))
                .andExpect(content().string(containsString("Are you one of these people?")));

        mvc.perform(post("/invites/{t}", token).with(user(ben)).with(csrf())
                        .param("member", String.valueOf(benMemberId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups/" + groupId));

        mvc.perform(get("/invites/{t}", token).with(user(ben)))
                .andExpect(redirectedUrl("/groups/" + groupId))
                .andExpect(flash().attribute("success", "You're already in Lisbon weekend."));
        mvc.perform(get("/groups/{g}", groupId).with(user(ben))).andExpect(status().isOk());
    }

    @Test
    void joiningWithANameThatIsTakenShowsTheReason() throws Exception {
        String token = inviteService.createLink(anaId, groupId);

        mvc.perform(post("/invites/{t}", token).with(user(ben)).with(csrf()).param("name", "Ben"))
                .andExpect(redirectedUrl("/invites/" + token))
                .andExpect(flash().attribute("error", "Ben is already in this group."));
    }

    @Test
    void badLinksShowAFriendlyNotFoundPage() throws Exception {
        mvc.perform(get("/invites/{t}", "not-a-real-token").with(user(ben)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("This invite link doesn’t work any more")))
                .andExpect(content().string(not(containsString("Lisbon weekend"))));
    }

    @Test
    void visitorsLogInOrSignUpFirstAndComeBackToTheInvite() throws Exception {
        String token = inviteService.createLink(anaId, groupId);
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/invites/{t}", token).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/signup").session(session))
                .andExpect(content().string(containsString("go straight back to your invite")));

        mvc.perform(post("/signup").session(session).with(csrf())
                        .param("displayName", "Carla")
                        .param("email", "carla@example.com")
                        .param("password", "correct horse battery")
                        .param("confirmPassword", "correct horse battery"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("http://localhost/invites/" + token + "*"));
    }

    @Test
    void homePageListsPeopleYouShareGroupsWith() throws Exception {
        inviteService.accept(inviteService.createLink(anaId, groupId), benId, benMemberId, null);

        mvc.perform(get("/").with(user(ana)))
                .andExpect(content().string(containsString("People you share groups with")))
                .andExpect(content().string(containsString("Benjamin")))
                .andExpect(content().string(not(containsString("ben@example.com"))));
    }
}
