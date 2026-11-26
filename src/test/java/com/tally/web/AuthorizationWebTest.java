package com.tally.web;

import com.tally.TestcontainersConfiguration;
import com.tally.account.AccountService;
import com.tally.comment.CommentService;
import com.tally.receipt.ReceiptService;
import com.tally.domain.Category;
import com.tally.service.DashboardService;
import com.tally.service.ExpenseCommand;
import com.tally.service.ExpenseService;
import com.tally.service.GroupCommand;
import com.tally.service.GroupService;
import com.tally.service.view.GroupDashboard;
import com.tally.security.TallyUser;
import com.tally.security.TallyUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Walks every group, expense, member, export, receipt and comment URL as someone who isn't in the group.
 * Each one must answer 404, exactly like a group that doesn't exist, and change nothing.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthorizationWebTest {

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
    ReceiptService receiptService;
    @Autowired
    CommentService commentService;

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    long anaId;
    long groupId;
    long memberId;
    long expenseId;
    long commentId;
    TallyUser mallory;

    @BeforeEach
    void anaHasAGroupAndMalloryIsNotInIt() {
        anaId = accountService.signUp("ana@example.com", "Ana", "correct horse battery");
        groupId = groupService.create(anaId, new GroupCommand("Ana's secret trip", null, "EUR"));
        memberId = groupService.addMember(anaId, groupId, "Ben");
        expenseId = expenseService.create(anaId, groupId, new ExpenseCommand("Hotel", new BigDecimal("90.00"),
                Category.ACCOMMODATION, LocalDate.now(), memberId, List.of(memberId)));
        receiptService.attach(anaId, groupId, expenseId, "hotel.png", PNG);
        commentId = commentService.add(anaId, groupId, expenseId, "Breakfast was included");

        accountService.signUp("mallory@example.com", "Mallory", "not invited at all");
        mallory = userDetailsService.loadUserByUsername("mallory@example.com");
    }

    @Test
    void outsiderGetsNotFoundOnEveryReadUrl() throws Exception {
        for (String url : List.of(
                "/groups/" + groupId,
                "/groups/" + groupId + "/edit",
                "/groups/" + groupId + "/expenses/new",
                "/groups/" + groupId + "/expenses/" + expenseId + "/edit",
                "/groups/" + groupId + "/export.csv",
                "/groups/" + groupId + "/activity",
                "/groups/" + groupId + "/expenses/" + expenseId,
                "/groups/" + groupId + "/expenses/" + expenseId + "/receipt",
                "/groups/" + groupId + "/expenses/" + expenseId + "/receipt?download=true")) {
            mvc.perform(get(url).with(user(mallory)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().string(not(containsString("Ana's secret trip"))));
        }
    }

    @Test
    void outsiderGetsNotFoundOnEveryChangeAndNothingChanges() throws Exception {
        List<MockHttpServletRequestBuilder> changes = List.of(
                post("/groups/{g}", groupId).param("name", "Hacked").param("currency", "EUR"),
                post("/groups/{g}/delete", groupId),
                post("/groups/{g}/members", groupId).param("name", "Mallory"),
                post("/groups/{g}/members/{m}/delete", groupId, memberId),
                post("/groups/{g}/expenses", groupId).param("description", "Fake").param("amount", "5")
                        .param("category", "OTHER").param("date", LocalDate.now().toString())
                        .param("paidById", String.valueOf(memberId)).param("participantIds", String.valueOf(memberId)),
                post("/groups/{g}/expenses/{e}", groupId, expenseId).param("description", "Changed")
                        .param("amount", "999").param("category", "OTHER").param("date", LocalDate.now().toString())
                        .param("paidById", String.valueOf(memberId)).param("participantIds", String.valueOf(memberId)),
                post("/groups/{g}/expenses/{e}/delete", groupId, expenseId),
                post("/groups/{g}/invites", groupId),
                post("/groups/{g}/invites/revoke", groupId),
                post("/groups/{g}/repayments", groupId).param("fromMemberId", String.valueOf(memberId))
                        .param("toMemberId", String.valueOf(memberId)).param("amount", "5"),
                multipart("/groups/{g}/expenses/{e}/receipt", groupId, expenseId)
                        .file(new MockMultipartFile("file", "evil.png", "image/png", PNG)),
                post("/groups/{g}/expenses/{e}/receipt/delete", groupId, expenseId),
                post("/groups/{g}/expenses/{e}/comments", groupId, expenseId).param("body", "Hello from outside"),
                post("/groups/{g}/expenses/{e}/comments/{c}/delete", groupId, expenseId, commentId));

        for (MockHttpServletRequestBuilder change : changes) {
            mvc.perform(change.with(user(mallory)).with(csrf())).andExpect(status().isNotFound());
        }

        GroupDashboard unchanged = dashboardService.dashboard(anaId, groupId);
        assertThat(unchanged.name()).isEqualTo("Ana's secret trip");
        assertThat(unchanged.members()).extracting("name").containsExactly("Ana", "Ben");
        assertThat(unchanged.expenses()).extracting("description").containsExactly("Hotel");
        assertThat(unchanged.totalSpent()).isEqualByComparingTo("90.00");
        assertThat(receiptService.info(anaId, groupId, expenseId)).get()
                .extracting("fileName").isEqualTo("hotel.png");
        assertThat(commentService.list(anaId, groupId, expenseId))
                .extracting("body").containsExactly("Breakfast was included");
    }

    @Test
    void outsiderDoesNotSeeTheGroupInTheirList() throws Exception {
        mvc.perform(get("/").with(user(mallory)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Ana's secret trip"))));
    }
}
