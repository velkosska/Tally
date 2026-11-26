package com.tally.service;

import com.tally.activity.ActivityLog;
import com.tally.activity.ActivityType;
import com.tally.activity.ExpenseSnapshot;
import com.tally.domain.Expense;
import com.tally.domain.Expense.ExpenseDetails;
import com.tally.domain.ExpenseGroup;
import com.tally.domain.ExpenseGroupRepository;
import com.tally.domain.Member;
import com.tally.service.view.ExpenseFormPage;
import com.tally.service.view.MemberOption;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Use cases for adding, editing and removing expenses. Every method checks the user belongs to the group. */
@Service
@Transactional
public class ExpenseService {

    private final ExpenseGroupRepository groups;
    private final ActivityLog activityLog;

    public ExpenseService(ExpenseGroupRepository groups, ActivityLog activityLog) {
        this.groups = groups;
        this.activityLog = activityLog;
    }

    @Transactional(readOnly = true)
    public ExpenseFormPage formPage(long userId, long groupId) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        List<MemberOption> members = group.getMembers().stream()
                .map(m -> new MemberOption(m.getId(), m.getName()))
                .toList();
        return new ExpenseFormPage(group.getId(), group.getName(), group.getCurrency(), members);
    }

    @Transactional(readOnly = true)
    public ExpenseCommand details(long userId, long groupId, long expenseId) {
        Expense expense = groups.getForUser(groupId, userId).findExpense(expenseId);
        List<Long> participantIds = expense.getParticipants().stream().map(Member::getId).toList();
        Map<Long, BigDecimal> weights = new LinkedHashMap<>();
        if (expense.getSplitMethod().usesWeights()) {
            expense.getWeights().forEach((member, weight) -> weights.put(member.getId(), weight));
        }
        return new ExpenseCommand(expense.getDescription(), expense.getAmount(), expense.getCategory(),
                expense.getDate(), expense.getPaidBy().getId(), participantIds, expense.getSplitMethod(), weights);
    }

    /** Records an expense and returns its id. Flushes so the id exists even before the transaction commits. */
    public long create(long userId, long groupId, ExpenseCommand command) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        Expense expense = group.addExpense(toDetails(group, command));
        groups.flush();
        activityLog.record(group, userId, ActivityType.EXPENSE_ADDED, expense.getDescription(),
                expense.getAmount(), ExpenseSnapshot.of(expense).summary());
        return expense.getId();
    }

    public void update(long userId, long groupId, long expenseId, ExpenseCommand command) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        ExpenseSnapshot before = ExpenseSnapshot.of(group.findExpense(expenseId));
        group.updateExpense(expenseId, toDetails(group, command));
        Expense after = group.findExpense(expenseId);
        activityLog.record(group, userId, ActivityType.EXPENSE_UPDATED, after.getDescription(),
                after.getAmount(), before.changesTo(ExpenseSnapshot.of(after)));
    }

    public void delete(long userId, long groupId, long expenseId) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        Expense expense = group.findExpense(expenseId);
        activityLog.record(group, userId, ActivityType.EXPENSE_DELETED, expense.getDescription(),
                expense.getAmount(), "paid by " + expense.getPaidBy().getName());
        group.removeExpense(expenseId);
    }

    private static ExpenseDetails toDetails(ExpenseGroup group, ExpenseCommand command) {
        Member payer = group.findMember(command.paidById());
        Map<Member, BigDecimal> participants = new LinkedHashMap<>();
        for (Long id : command.participantIds()) {
            participants.put(group.findMember(id), command.weights().get(id));
        }
        return new ExpenseDetails(command.description(), command.amount(), command.category(),
                command.date(), payer, command.splitMethod(), participants);
    }
}
