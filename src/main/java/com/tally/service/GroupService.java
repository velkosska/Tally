package com.tally.service;

import com.tally.domain.Expense;
import com.tally.domain.ExpenseGroup;
import com.tally.domain.ExpenseGroupRepository;
import com.tally.domain.Member;
import com.tally.service.view.GroupCard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Use cases for groups and their members. */
@Service
@Transactional
public class GroupService {

    private final ExpenseGroupRepository groups;

    public GroupService(ExpenseGroupRepository groups) {
        this.groups = groups;
    }

    @Transactional(readOnly = true)
    public List<GroupCard> listGroups() {
        return groups.findAllByOrderByCreatedAtDesc().stream()
                .map(GroupService::toCard)
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupCommand details(long groupId) {
        ExpenseGroup group = groups.getOrThrow(groupId);
        return new GroupCommand(group.getName(), group.getDescription(), group.getCurrency());
    }

    public long create(GroupCommand command) {
        ExpenseGroup group = new ExpenseGroup(command.name(), command.description(), command.currency());
        return groups.save(group).getId();
    }

    public void update(long groupId, GroupCommand command) {
        groups.getOrThrow(groupId).updateDetails(command.name(), command.description(), command.currency());
    }

    public void delete(long groupId) {
        ExpenseGroup group = groups.getOrThrow(groupId);
        group.removeAllExpenses();
        groups.flush();
        groups.delete(group);
    }

    /** Adds a member and returns their id. Flushes so the id exists even before the transaction commits. */
    public long addMember(long groupId, String name) {
        Member member = groups.getOrThrow(groupId).addMember(name);
        groups.flush();
        return member.getId();
    }

    public void removeMember(long groupId, long memberId) {
        groups.getOrThrow(groupId).removeMember(memberId);
    }

    private static GroupCard toCard(ExpenseGroup group) {
        BigDecimal total = group.getExpenses().stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> names = group.getMembers().stream().map(Member::getName).toList();
        return new GroupCard(group.getId(), group.getName(), group.getDescription(), group.getCurrency(),
                names, group.getExpenses().size(), total);
    }
}
