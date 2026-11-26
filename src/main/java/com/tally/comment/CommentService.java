package com.tally.comment;

import com.tally.account.AppUser;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Comments on expenses. Any member can comment; only the author can delete their comment. */
@Service
@Transactional
public class CommentService {

    private final CommentRepository comments;
    private final ExpenseGroupRepository groups;
    private final AppUserRepository users;
    private final ActivityLog activityLog;

    public CommentService(CommentRepository comments, ExpenseGroupRepository groups, AppUserRepository users,
                          ActivityLog activityLog) {
        this.comments = comments;
        this.groups = groups;
        this.users = users;
        this.activityLog = activityLog;
    }

    public long add(long userId, long groupId, long expenseId, String body) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        Expense expense = group.findExpense(expenseId);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) {
            throw new BusinessRuleException("Write something before posting.");
        }
        if (text.length() > Comment.MAX_LENGTH) {
            throw new BusinessRuleException("Keep comments under " + Comment.MAX_LENGTH + " characters.");
        }
        AppUser author = users.findById(userId).orElseThrow();
        Comment comment = comments.save(new Comment(expense, author, text));
        activityLog.record(group, userId, ActivityType.COMMENT_ADDED, expense.getDescription(), null,
                text.length() > 80 ? text.substring(0, 79) + "…" : text);
        return comment.getId();
    }

    @Transactional(readOnly = true)
    public List<CommentRow> list(long userId, long groupId, long expenseId) {
        groups.getForUser(groupId, userId).findExpense(expenseId);
        return comments.findForExpense(expenseId).stream()
                .map(c -> new CommentRow(c.getId(), c.getAuthorName(), c.isWrittenBy(userId), c.getBody(),
                        c.getCreatedAt()))
                .toList();
    }

    public void delete(long userId, long groupId, long expenseId, long commentId) {
        groups.getForUser(groupId, userId).findExpense(expenseId);
        Comment comment = comments.findById(commentId)
                .filter(c -> c.getExpense().getId() == expenseId)
                .orElseThrow(() -> new NotFoundException("Comment", commentId));
        if (!comment.isWrittenBy(userId)) {
            throw new BusinessRuleException("You can only delete your own comments.");
        }
        comments.delete(comment);
    }

    /** Expense id to number of comments, for a group the user is in. */
    @Transactional(readOnly = true)
    public Map<Long, Long> countsForGroup(long groupId) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : comments.countByExpense(groupId)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }
}
