package com.tally.web;

import com.tally.comment.Comment;
import com.tally.comment.CommentService;
import com.tally.domain.BusinessRuleException;
import com.tally.receipt.ReceiptFile;
import com.tally.receipt.ReceiptService;
import com.tally.security.TallyUser;
import com.tally.service.DashboardService;
import com.tally.service.GroupService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

/** An expense's own page: its split, its receipt and the comments about it. */
@Controller
@RequestMapping("/groups/{groupId}/expenses/{expenseId}")
public class ExpenseDetailController {

    private final DashboardService dashboardService;
    private final GroupService groupService;
    private final ReceiptService receiptService;
    private final CommentService commentService;

    public ExpenseDetailController(DashboardService dashboardService, GroupService groupService,
                                   ReceiptService receiptService, CommentService commentService) {
        this.dashboardService = dashboardService;
        this.groupService = groupService;
        this.receiptService = receiptService;
        this.commentService = commentService;
    }

    @GetMapping
    public String show(@AuthenticationPrincipal TallyUser user, @PathVariable long groupId,
                       @PathVariable long expenseId, Model model) {
        var group = groupService.details(user.id(), groupId);
        model.addAttribute("groupId", groupId);
        model.addAttribute("groupName", group.name());
        model.addAttribute("currency", group.currency());
        model.addAttribute("expense", dashboardService.expenseRow(user.id(), groupId, expenseId));
        model.addAttribute("receipt", receiptService.info(user.id(), groupId, expenseId).orElse(null));
        model.addAttribute("comments", commentService.list(user.id(), groupId, expenseId));
        model.addAttribute("maxCommentLength", Comment.MAX_LENGTH);
        return "expenses/detail";
    }

    // ---- receipt ----

    @PostMapping("/receipt")
    public String uploadReceipt(@AuthenticationPrincipal TallyUser user, @PathVariable long groupId,
                                @PathVariable long expenseId, @RequestParam(name = "file", required = false) MultipartFile file,
                                RedirectAttributes redirect) throws IOException {
        try {
            byte[] data = file == null ? new byte[0] : file.getBytes();
            receiptService.attach(user.id(), groupId, expenseId, file == null ? null : file.getOriginalFilename(), data);
            Flash.success(redirect, "Receipt attached.");
        } catch (BusinessRuleException e) {
            Flash.error(redirect, e.getMessage());
        }
        return redirectToExpense(groupId, expenseId, "receipt");
    }

    /**
     * Sends the receipt file. Images open in the browser; PDFs always download, because a PDF viewer
     * can run scripts. Either way the browser is told not to guess the type and to treat the file as
     * a sandboxed document, so nothing in it can act on Tally's behalf.
     */
    @GetMapping("/receipt")
    public ResponseEntity<byte[]> receipt(@AuthenticationPrincipal TallyUser user, @PathVariable long groupId,
                                          @PathVariable long expenseId,
                                          @RequestParam(name = "download", defaultValue = "false") boolean download) {
        ReceiptFile file = receiptService.file(user.id(), groupId, expenseId);
        boolean inline = file.type().isImage() && !download;
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.fileName())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.type().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; sandbox")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(file.data());
    }

    @PostMapping("/receipt/delete")
    public String removeReceipt(@AuthenticationPrincipal TallyUser user, @PathVariable long groupId,
                                @PathVariable long expenseId, RedirectAttributes redirect) {
        receiptService.remove(user.id(), groupId, expenseId);
        Flash.success(redirect, "Receipt removed.");
        return redirectToExpense(groupId, expenseId, "receipt");
    }

    // ---- comments ----

    @PostMapping("/comments")
    public String comment(@AuthenticationPrincipal TallyUser user, @PathVariable long groupId,
                          @PathVariable long expenseId, @RequestParam(name = "body", required = false) String body,
                          RedirectAttributes redirect) {
        try {
            commentService.add(user.id(), groupId, expenseId, body);
        } catch (BusinessRuleException e) {
            Flash.error(redirect, e.getMessage());
            redirect.addFlashAttribute("draft", body);
        }
        return redirectToExpense(groupId, expenseId, "comments");
    }

    @PostMapping("/comments/{commentId}/delete")
    public String deleteComment(@AuthenticationPrincipal TallyUser user, @PathVariable long groupId,
                                @PathVariable long expenseId, @PathVariable long commentId,
                                RedirectAttributes redirect) {
        try {
            commentService.delete(user.id(), groupId, expenseId, commentId);
            Flash.success(redirect, "Comment deleted.");
        } catch (BusinessRuleException e) {
            Flash.error(redirect, e.getMessage());
        }
        return redirectToExpense(groupId, expenseId, "comments");
    }

    private static String redirectToExpense(long groupId, long expenseId, String section) {
        return "redirect:/groups/" + groupId + "/expenses/" + expenseId + "#" + section;
    }
}
