package com.tally.activity;

import com.tally.account.AppUserRepository;
import com.tally.domain.ExpenseGroup;
import com.tally.domain.ExpenseGroupRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Writes and reads the activity feed. Services call {@link #record} inside their own transaction,
 * so a change and its feed entry are saved together or not at all.
 */
@Service
@Transactional(readOnly = true)
public class ActivityLog {

    public static final int PAGE_SIZE = 30;

    private final ActivityRepository activities;
    private final ExpenseGroupRepository groups;
    private final AppUserRepository users;

    public ActivityLog(ActivityRepository activities, ExpenseGroupRepository groups, AppUserRepository users) {
        this.activities = activities;
        this.groups = groups;
        this.users = users;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(ExpenseGroup group, long actorUserId, ActivityType type, String subject,
                       BigDecimal amount, String details) {
        users.findById(actorUserId).ifPresent(actor ->
                activities.save(new Activity(group, actor, type, subject, amount, details)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(ExpenseGroup group, long actorUserId, ActivityType type, String subject) {
        record(group, actorUserId, type, subject, null, null);
    }

    /**
     * Deletes a group's feed; called just before the group is deleted. Clears the persistence context,
     * so callers must load the group again afterwards.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteForGroup(long groupId) {
        activities.deleteForGroup(groupId);
    }

    /** One page of a group's feed (page 0 is the newest), for a user who is in the group. */
    public Page<ActivityRow> forGroup(long userId, long groupId, int page) {
        groups.getForUser(groupId, userId);
        return activities.findForGroup(groupId, PageRequest.of(Math.max(page, 0), PAGE_SIZE))
                .map(activity -> toRow(activity, userId));
    }

    /** The latest changes across all of the user's groups, for the home page. */
    public List<ActivityRow> latestForUser(long userId, int limit) {
        return activities.findLatestForUser(userId, PageRequest.of(0, limit)).stream()
                .map(activity -> toRow(activity, userId))
                .toList();
    }

    private static ActivityRow toRow(Activity activity, long userId) {
        ExpenseGroup group = activity.getGroup();
        boolean byYou = activity.getActor() != null && activity.getActor().getId() == userId;
        return new ActivityRow(group.getId(), group.getName(), group.getCurrency(), activity.getActorName(), byYou,
                activity.getType(), activity.getSubject(), activity.getAmount(), activity.getDetails(),
                activity.getCreatedAt());
    }
}
