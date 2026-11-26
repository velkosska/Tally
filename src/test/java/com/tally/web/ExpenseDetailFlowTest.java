package com.tally.web;

import com.tally.TestcontainersConfiguration;
import com.tally.account.AccountService;
import com.tally.activity.ActivityLog;
import com.tally.activity.ActivityRow;
import com.tally.activity.ActivityType;
import com.tally.comment.CommentService;
import com.tally.domain.BusinessRuleException;
import com.tally.domain.Category;
import com.tally.invite.InviteService;
import com.tally.receipt.ReceiptService;
import com.tally.security.TallyUser;
import com.tally.security.TallyUserDetailsService;
import com.tally.service.DashboardService;
import com.tally.service.ExpenseCommand;
import com.tally.service.ExpenseService;
import com.tally.service.GroupCommand;
import com.tally.service.GroupService;
import com.tally.service.view.ExpenseRow;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An expense's own page: attaching and serving receipts, and commenting. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExpenseDetailFlowTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] PDF = "%PDF-1.4\n%fake but recognisable\n".getBytes(StandardCharsets.US_ASCII);

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
    ReceiptService receiptService;
    @Autowired
    CommentService commentService;
    @Autowired
    ActivityLog activityLog;
    @Autowired
    EntityManager entityManager;
    @Autowired
    JdbcTemplate jdbc;

    long anaId;
    long benId;
    TallyUser ana;
    TallyUser ben;
    long groupId;
    long expenseId;
    String expenseUrl;

    @BeforeEach
    void anaAndBenShareADinner() {
        anaId = accountService.signUp("ana@example.com", "Ana", "correct horse battery");
        benId = accountService.signUp("ben@example.com", "Ben", "another long password");
        ana = userDetailsService.loadUserByUsername("ana@example.com");
        ben = userDetailsService.loadUserByUsername("ben@example.com");
        groupId = groupService.create(anaId, new GroupCommand("Lisbon", null, "EUR"));
        inviteService.accept(inviteService.createLink(anaId, groupId), benId, null, "Ben");
        List<Long> members = dashboardService.dashboard(anaId, groupId).members().stream().map(m -> m.id()).toList();
        expenseId = expenseService.create(anaId, groupId, new ExpenseCommand("Seafood dinner",
                new BigDecimal("90.00"), Category.FOOD, LocalDate.now(), members.getFirst(), members));
        expenseUrl = "/groups/" + groupId + "/expenses/" + expenseId;
    }

    @Test
    void expensePageShowsTheSplitAndEmptySections() throws Exception {
        mvc.perform(get(expenseUrl).with(user(ana)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Seafood dinner")))
                .andExpect(content().string(containsString("Split equally")))
                .andExpect(content().string(containsString("€45.00")))
                .andExpect(content().string(containsString("Add a photo or PDF of the receipt")))
                .andExpect(content().string(containsString("No comments yet")));
    }

    @Test
    void anImageReceiptIsStoredAndShownInline() throws Exception {
        mvc.perform(multipart(expenseUrl + "/receipt").file(file("dinner bill.png", "image/png", PNG))
                        .with(user(ana)).with(csrf()))
                .andExpect(redirectedUrl(expenseUrl + "#receipt"))
                .andExpect(flash().attribute("success", "Receipt attached."));
        reloadFromDatabase();

        mvc.perform(get(expenseUrl + "/receipt").with(user(ben)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Content-Disposition", containsString("dinner bill.png")))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().bytes(PNG));
        mvc.perform(get(expenseUrl + "/receipt").param("download", "true").with(user(ben)))
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
        mvc.perform(get(expenseUrl).with(user(ben)))
                .andExpect(content().string(containsString("dinner bill.png")))
                .andExpect(content().string(containsString("<img")))
                .andExpect(content().string(containsString("Replace it with another photo or PDF")));
    }

    @Test
    void aPdfReceiptAlwaysDownloads() throws Exception {
        receiptService.attach(anaId, groupId, expenseId, "scan", PDF);

        mvc.perform(get(expenseUrl + "/receipt").with(user(ana)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("scan.pdf")));
        mvc.perform(get(expenseUrl).with(user(ana)))
                .andExpect(content().string(not(containsString("<img"))));
    }

    @Test
    void theTypeComesFromTheBytesNotTheNameOrTheBrowser() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        mvc.perform(multipart(expenseUrl + "/receipt").file(file("receipt.png", "image/png", html))
                        .with(user(ana)).with(csrf()))
                .andExpect(redirectedUrl(expenseUrl + "#receipt"))
                .andExpect(flash().attribute("error", "Receipts can be JPEG, PNG or WebP photos, or PDF files."));
        assertThat(receiptService.info(anaId, groupId, expenseId)).isEmpty();

        receiptService.attach(anaId, groupId, expenseId, "photo.pdf", PNG);
        assertThat(receiptService.info(anaId, groupId, expenseId).orElseThrow().fileName()).isEqualTo("photo.pdf.png");
    }

    @Test
    void emptyAndOversizedFilesAreRejected() throws Exception {
        mvc.perform(multipart(expenseUrl + "/receipt").file(file("empty.png", "image/png", new byte[0]))
                        .with(user(ana)).with(csrf()))
                .andExpect(flash().attribute("error", "Pick a photo or PDF of the receipt first."));

        byte[] huge = Arrays.copyOf(PNG, ReceiptService.MAX_BYTES + 1);
        assertThatThrownBy(() -> receiptService.attach(anaId, groupId, expenseId, "huge.png", huge))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("over 5 MB");
        assertThat(receiptService.info(anaId, groupId, expenseId)).isEmpty();
    }

    @Test
    void aNewReceiptReplacesTheOldOneAndCanBeRemoved() throws Exception {
        receiptService.attach(anaId, groupId, expenseId, "first.png", PNG);
        receiptService.attach(benId, groupId, expenseId, "second.pdf", PDF);
        reloadFromDatabase();

        assertThat(jdbc.queryForObject("select count(*) from expense_receipt", Integer.class)).isEqualTo(1);
        assertThat(receiptService.info(anaId, groupId, expenseId).orElseThrow().fileName()).isEqualTo("second.pdf");

        mvc.perform(post(expenseUrl + "/receipt/delete").with(user(ana)).with(csrf()))
                .andExpect(redirectedUrl(expenseUrl + "#receipt"));
        assertThat(receiptService.info(anaId, groupId, expenseId)).isEmpty();
        mvc.perform(get(expenseUrl + "/receipt").with(user(ana))).andExpect(status().isNotFound());

        assertThat(feed()).extracting(ActivityRow::who, ActivityRow::type).startsWith(
                tuple("You", ActivityType.RECEIPT_REMOVED),
                tuple("Ben", ActivityType.RECEIPT_REPLACED),
                tuple("You", ActivityType.RECEIPT_ADDED));
    }

    @Test
    void membersCanCommentAndOnlyDeleteTheirOwn() throws Exception {
        mvc.perform(post(expenseUrl + "/comments").param("body", "  Did this include the tip?  ")
                        .with(user(ben)).with(csrf()))
                .andExpect(redirectedUrl(expenseUrl + "#comments"));
        long bensComment = commentService.list(benId, groupId, expenseId).getFirst().id();

        mvc.perform(get(expenseUrl).with(user(ana)))
                .andExpect(content().string(containsString("Comments (1)")))
                .andExpect(content().string(containsString("Did this include the tip?")));
        assertThat(commentService.list(anaId, groupId, expenseId))
                .extracting("who", "body").containsExactly(tuple("Ben", "Did this include the tip?"));
        assertThat(commentService.list(benId, groupId, expenseId).getFirst().who()).isEqualTo("You");

        mvc.perform(post(expenseUrl + "/comments/{c}/delete", bensComment).with(user(ana)).with(csrf()))
                .andExpect(flash().attribute("error", "You can only delete your own comments."));
        assertThat(commentService.list(anaId, groupId, expenseId)).hasSize(1);

        mvc.perform(post(expenseUrl + "/comments/{c}/delete", bensComment).with(user(ben)).with(csrf()))
                .andExpect(flash().attribute("success", "Comment deleted."));
        assertThat(commentService.list(anaId, groupId, expenseId)).isEmpty();

        ActivityRow commented = feed().getFirst();
        assertThat(commented).extracting(ActivityRow::who, ActivityRow::type, ActivityRow::subject, ActivityRow::details)
                .containsExactly("Ben", ActivityType.COMMENT_ADDED, "Seafood dinner", "Did this include the tip?");
    }

    @Test
    void blankAndOverlongCommentsAreRejectedAndTheDraftIsKept() throws Exception {
        mvc.perform(post(expenseUrl + "/comments").param("body", "   ").with(user(ana)).with(csrf()))
                .andExpect(flash().attribute("error", "Write something before posting."));

        String tooLong = "a".repeat(1001);
        mvc.perform(post(expenseUrl + "/comments").param("body", tooLong).with(user(ana)).with(csrf()))
                .andExpect(flash().attribute("error", "Keep comments under 1000 characters."))
                .andExpect(flash().attribute("draft", tooLong));
        assertThat(commentService.list(anaId, groupId, expenseId)).isEmpty();
    }

    @Test
    void longCommentsAreShortenedInTheActivityFeed() {
        commentService.add(anaId, groupId, expenseId, "x".repeat(200));

        assertThat(feed().getFirst().details()).hasSize(80).endsWith("…");
    }

    @Test
    void groupPageShowsReceiptAndCommentBadges() throws Exception {
        receiptService.attach(anaId, groupId, expenseId, "bill.png", PNG);
        commentService.add(anaId, groupId, expenseId, "Paid by card");
        commentService.add(benId, groupId, expenseId, "Thanks!");

        ExpenseRow row = dashboardService.dashboard(anaId, groupId).expenses().getFirst();
        assertThat(row.hasReceipt()).isTrue();
        assertThat(row.commentCount()).isEqualTo(2);
        mvc.perform(get("/groups/{id}", groupId).with(user(ana)))
                .andExpect(content().string(containsString(expenseUrl + "\"")))
                .andExpect(content().string(containsString("has a receipt")))
                .andExpect(content().string(containsString("2 comments")));
    }

    @Test
    void deletingTheExpenseDeletesItsReceiptAndComments() {
        receiptService.attach(anaId, groupId, expenseId, "bill.png", PNG);
        commentService.add(anaId, groupId, expenseId, "Paid by card");
        reloadFromDatabase();

        expenseService.delete(anaId, groupId, expenseId);
        reloadFromDatabase();

        assertThat(jdbc.queryForObject("select count(*) from expense_receipt", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from expense_comment", Integer.class)).isZero();
    }

    @Test
    void deletingTheGroupDeletesEverythingInIt() {
        receiptService.attach(anaId, groupId, expenseId, "bill.png", PNG);
        commentService.add(benId, groupId, expenseId, "Paid by card");
        reloadFromDatabase();

        groupService.delete(anaId, groupId);
        reloadFromDatabase();

        assertThat(jdbc.queryForObject("select count(*) from expense_receipt", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from expense_comment", Integer.class)).isZero();
    }

    @Test
    void anExpenseFromAnotherGroupIsNotFound() throws Exception {
        long otherGroup = groupService.create(anaId, new GroupCommand("Other", null, "EUR"));

        mvc.perform(get("/groups/{g}/expenses/{e}", otherGroup, expenseId).with(user(ana)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/groups/{g}/expenses/{e}/comments", otherGroup, expenseId).param("body", "hi")
                        .with(user(ana)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    private List<ActivityRow> feed() {
        return activityLog.forGroup(anaId, groupId, 0).getContent();
    }

    private void reloadFromDatabase() {
        entityManager.flush();
        entityManager.clear();
    }

    private static MockMultipartFile file(String name, String contentType, byte[] data) {
        return new MockMultipartFile("file", name, contentType, data);
    }
}
