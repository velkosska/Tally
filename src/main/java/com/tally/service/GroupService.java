package com.tally.service;

import com.tally.account.AppUser;
import com.tally.account.AppUserRepository;
import com.tally.domain.Expense;
import com.tally.domain.ExpenseGroup;
import com.tally.domain.ExpenseGroupRepository;
import com.tally.domain.Member;
import com.tally.service.view.GroupCard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Use cases for groups and their members. Every method only sees groups the given user belongs to. */
@Service
@Transactional
public class GroupService {

    private final ExpenseGroupRepository groups;
    private final AppUserRepository users;

    public GroupService(ExpenseGroupRepository groups, AppUserRepository users) {
        this.groups = groups;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<GroupCard> listGroups(long userId) {
        return groups.findAllForUser(userId).stream()
                .map(GroupService::toCard)
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupCommand details(long userId, long groupId) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        return new GroupCommand(group.getName(), group.getDescription(), group.getCurrency());
    }

    /** Creates a group with its creator as the first member, linked to their account. */
    public long create(long userId, GroupCommand command) {
        AppUser creator = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User " + userId + " does not exist"));
        ExpenseGroup group = new ExpenseGroup(command.name(), command.description(), command.currency());
        group.addMember(creator.getDisplayName(), creator);
        return groups.saveAndFlush(group).getId();
    }

    public void update(long userId, long groupId, GroupCommand command) {
        groups.getForUser(groupId, userId).updateDetails(command.name(), command.description(), command.currency());
    }

    public void delete(long userId, long groupId) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        group.removeAllExpenses();
        groups.flush();
        groups.delete(group);
    }

    /** Adds a member and returns their id. Flushes so the id exists even before the transaction commits. */
    public long addMember(long userId, long groupId, String name) {
        Member member = groups.getForUser(groupId, userId).addMember(name);
        groups.flush();
        return member.getId();
    }

    public void removeMember(long userId, long groupId, long memberId) {
        groups.getForUser(groupId, userId).removeMember(memberId);
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
