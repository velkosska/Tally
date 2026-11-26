package com.tally.service;

import com.tally.activity.ActivityLog;
import com.tally.activity.ActivityType;
import com.tally.account.AppUser;
import com.tally.account.AppUserRepository;
import com.tally.domain.Expense;
import com.tally.domain.ExpenseGroup;
import com.tally.domain.ExpenseGroupRepository;
import com.tally.domain.Member;
import com.tally.service.view.Friend;
import com.tally.service.view.GroupCard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Use cases for groups and their members. Every method only sees groups the given user belongs to. */
@Service
@Transactional
public class GroupService {

    private final ExpenseGroupRepository groups;
    private final AppUserRepository users;
    private final ActivityLog activityLog;

    public GroupService(ExpenseGroupRepository groups, AppUserRepository users, ActivityLog activityLog) {
        this.groups = groups;
        this.users = users;
        this.activityLog = activityLog;
    }

    @Transactional(readOnly = true)
    public List<GroupCard> listGroups(long userId) {
        return groups.findAllForUser(userId).stream()
                .map(GroupService::toCard)
                .toList();
    }

    /** People with an account who share a group with this user. */
    @Transactional(readOnly = true)
    public List<Friend> listFriends(long userId) {
        return groups.findFriends(userId).stream()
                .map(shared -> new Friend(shared.displayName(), shared.sharedGroups()))
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
        long id = groups.saveAndFlush(group).getId();
        activityLog.record(group, userId, ActivityType.GROUP_CREATED, null);
        return id;
    }

    public void update(long userId, long groupId, GroupCommand command) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        List<String> changes = new ArrayList<>();
        String oldName = group.getName();
        String oldDescription = group.getDescription();
        String oldCurrency = group.getCurrency();
        group.updateDetails(command.name(), command.description(), command.currency());
        if (!oldName.equals(group.getName())) {
            changes.add("name " + oldName + " → " + group.getName());
        }
        if (!Objects.equals(oldDescription, group.getDescription())) {
            changes.add("new description");
        }
        if (!oldCurrency.equals(group.getCurrency())) {
            changes.add("currency " + oldCurrency + " → " + group.getCurrency());
        }
        if (!changes.isEmpty()) {
            activityLog.record(group, userId, ActivityType.GROUP_UPDATED, null, null, String.join("; ", changes));
        }
    }

    public void delete(long userId, long groupId) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        group.removeAllExpenses();
        groups.flush();
        activityLog.deleteForGroup(groupId);
        groups.deleteById(groupId);
    }

    /** Adds a member and returns their id. Flushes so the id exists even before the transaction commits. */
    public long addMember(long userId, long groupId, String name) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        Member member = group.addMember(name);
        groups.flush();
        activityLog.record(group, userId, ActivityType.MEMBER_ADDED, member.getName());
        return member.getId();
    }

    public void removeMember(long userId, long groupId, long memberId) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        String name = group.findMember(memberId).getName();
        group.removeMember(memberId);
        activityLog.record(group, userId, ActivityType.MEMBER_REMOVED, name);
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
