package com.tally.receipt;

import com.tally.account.AppUserRepository;
import com.tally.activity.ActivityLog;
import com.tally.activity.ActivityType;
import com.tally.domain.BusinessRuleException;
import com.tally.domain.Expense;
import com.tally.domain.ExpenseGroup;
import com.tally.domain.ExpenseGroupRepository;
import com.tally.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Attaching, viewing and removing an expense's receipt. Every method checks the user is in the group. */
@Service
@Transactional
public class ReceiptService {

    /** Big enough for a phone photo, small enough for the free database tier. */
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private final ReceiptRepository receipts;
    private final ExpenseGroupRepository groups;
    private final AppUserRepository users;
    private final ActivityLog activityLog;

    public ReceiptService(ReceiptRepository receipts, ExpenseGroupRepository groups, AppUserRepository users,
                          ActivityLog activityLog) {
        this.receipts = receipts;
        this.groups = groups;
        this.users = users;
        this.activityLog = activityLog;
    }

    /** Attaches a receipt to an expense, replacing the one it had. */
    public void attach(long userId, long groupId, long expenseId, String originalName, byte[] data) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        Expense expense = group.findExpense(expenseId);
        if (data == null || data.length == 0) {
            throw new BusinessRuleException("Pick a photo or PDF of the receipt first.");
        }
        if (data.length > MAX_BYTES) {
            throw new BusinessRuleException("That file is over 5 MB. Try a smaller photo.");
        }
        ReceiptType type = ReceiptType.detect(data).orElseThrow(() ->
                new BusinessRuleException("Receipts can be JPEG, PNG or WebP photos, or PDF files."));
        boolean replacing = receipts.deleteByExpense(expenseId) > 0;
        receipts.save(new Receipt(expense, cleanName(originalName, type), type, data, users.getReferenceById(userId)));
        activityLog.record(group, userId, replacing ? ActivityType.RECEIPT_REPLACED : ActivityType.RECEIPT_ADDED,
                expense.getDescription());
    }

    @Transactional(readOnly = true)
    public Optional<ReceiptInfo> info(long userId, long groupId, long expenseId) {
        groups.getForUser(groupId, userId).findExpense(expenseId);
        return receipts.findInfo(expenseId);
    }

    @Transactional(readOnly = true)
    public ReceiptFile file(long userId, long groupId, long expenseId) {
        groups.getForUser(groupId, userId).findExpense(expenseId);
        Receipt receipt = receipts.findByExpenseId(expenseId)
                .orElseThrow(() -> new NotFoundException("Receipt for expense", expenseId));
        ReceiptType type = ReceiptType.fromContentType(receipt.getContentType())
                .orElseThrow(() -> new IllegalStateException("Unknown receipt type " + receipt.getContentType()));
        return new ReceiptFile(receipt.getFileName(), type, receipt.getData());
    }

    public void remove(long userId, long groupId, long expenseId) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        Expense expense = group.findExpense(expenseId);
        if (receipts.deleteByExpense(expenseId) > 0) {
            activityLog.record(group, userId, ActivityType.RECEIPT_REMOVED, expense.getDescription());
        }
    }

    /** Keeps only safe characters, so the name can go in a download header as is. */
    static String cleanName(String originalName, ReceiptType type) {
        String name = originalName == null ? "" : originalName.replaceAll("[^A-Za-z0-9._ -]", "").strip();
        if (name.isEmpty() || name.startsWith(".")) {
            name = "receipt";
        }
        if (name.length() > 150) {
            name = name.substring(0, 150);
        }
        String extension = switch (type) {
            case JPEG -> ".jpg";
            case PNG -> ".png";
            case WEBP -> ".webp";
            case PDF -> ".pdf";
        };
        String lower = name.toLowerCase();
        boolean hasExtension = lower.endsWith(extension) || (type == ReceiptType.JPEG && lower.endsWith(".jpeg"));
        return hasExtension ? name : name + extension;
    }
}
