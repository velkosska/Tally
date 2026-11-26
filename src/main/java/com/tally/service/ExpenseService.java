package com.tally.service;

import com.tally.domain.Expense;
import com.tally.domain.Expense.ExpenseDetails;
import com.tally.domain.ExpenseGroup;
import com.tally.domain.ExpenseGroupRepository;
import com.tally.domain.Member;
import com.tally.service.view.ExpenseFormPage;
import com.tally.service.view.MemberOption;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Use cases for adding, editing and removing expenses. Every method checks the user belongs to the group. */
@Service
@Transactional
public class ExpenseService {

    private final ExpenseGroupRepository groups;

    public ExpenseService(ExpenseGroupRepository groups) {
        this.groups = groups;
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
        return new ExpenseCommand(expense.getDescription(), expense.getAmount(), expense.getCategory(),
                expense.getDate(), expense.getPaidBy().getId(), participantIds);
    }

    /** Records an expense and returns its id. Flushes so the id exists even before the transaction commits. */
    public long create(long userId, long groupId, ExpenseCommand command) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        Expense expense = group.addExpense(toDetails(group, command));
        groups.flush();
        return expense.getId();
    }

    public void update(long userId, long groupId, long expenseId, ExpenseCommand command) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        group.updateExpense(expenseId, toDetails(group, command));
    }

    public void delete(long userId, long groupId, long expenseId) {
        groups.getForUser(groupId, userId).removeExpense(expenseId);
    }

    private static ExpenseDetails toDetails(ExpenseGroup group, ExpenseCommand command) {
        Member payer = group.findMember(command.paidById());
        List<Member> participants = command.participantIds().stream()
                .map(group::findMember)
                .toList();
        return new ExpenseDetails(command.description(), command.amount(), command.category(),
                command.date(), payer, participants);
    }
}
