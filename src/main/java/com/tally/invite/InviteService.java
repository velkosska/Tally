package com.tally.invite;

import com.tally.account.AppUser;
import com.tally.account.AppUserRepository;
import com.tally.domain.BusinessRuleException;
import com.tally.domain.ExpenseGroup;
import com.tally.domain.ExpenseGroupRepository;
import com.tally.domain.Member;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Invite links. Any member can create one and share it however they like (chat, text message);
 * Tally never sends emails. Whoever opens a valid link and is logged in can join the group, either
 * as someone new or by claiming a name that was added before they had an account.
 */
@Service
@Transactional
public class InviteService {

    /** How long a link works. Long enough to share in a group chat, short enough that old links die. */
    public static final Duration VALIDITY = Duration.ofDays(14);

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_NAME_LENGTH = 50;

    private final GroupInviteRepository invites;
    private final ExpenseGroupRepository groups;
    private final AppUserRepository users;
    private final SecureRandom random = new SecureRandom();

    public InviteService(GroupInviteRepository invites, ExpenseGroupRepository groups, AppUserRepository users) {
        this.invites = invites;
        this.groups = groups;
        this.users = users;
    }

    /** Creates a new link for a group the user belongs to and returns its secret token (shown once). */
    public String createLink(long userId, long groupId) {
        ExpenseGroup group = groups.getForUser(groupId, userId);
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        invites.save(new GroupInvite(group, hash(token), user(userId), now, now.plus(VALIDITY)));
        return token;
    }

    /** Switches off every link of the group, for example after one was shared in the wrong chat. */
    public void revokeAll(long userId, long groupId) {
        groups.getForUser(groupId, userId);
        invites.revokeAll(groupId, Instant.now());
    }

    @Transactional(readOnly = true)
    public long countActiveLinks(long userId, long groupId) {
        groups.getForUser(groupId, userId);
        return invites.countActive(groupId, Instant.now());
    }

    /** What the invite page shows: the group and the names the user could claim. */
    @Transactional(readOnly = true)
    public InvitePreview preview(String token, long userId) {
        ExpenseGroup group = usableInvite(token).getGroup();
        return new InvitePreview(group.getId(), group.getName(), group.getDescription(),
                group.getMembers().stream().map(Member::getName).toList(),
                claimable(group), user(userId).getDisplayName());
    }

    @Transactional(readOnly = true)
    public boolean isMember(String token, long userId) {
        return usableInvite(token).getGroup().hasMemberLinkedTo(userId);
    }

    /**
     * Joins the invite's group and returns its id. With a member id, the user takes over that name
     * (and its expenses); without one, they join as a new member called {@code newName}
     * (or their own display name when that's blank).
     */
    public long accept(String token, long userId, Long claimMemberId, String newName) {
        ExpenseGroup group = usableInvite(token).getGroup();
        if (group.hasMemberLinkedTo(userId)) {
            return group.getId();
        }
        AppUser user = user(userId);
        if (claimMemberId != null) {
            group.claimMember(claimMemberId, user);
        } else {
            String name = newName == null || newName.isBlank() ? user.getDisplayName() : newName.strip();
            if (name.length() > MAX_NAME_LENGTH) {
                throw new BusinessRuleException("Keep your name to " + MAX_NAME_LENGTH + " characters or fewer.");
            }
            group.addMember(name, user);
        }
        groups.flush();
        return group.getId();
    }

    private GroupInvite usableInvite(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw new InvalidInviteException();
        }
        return invites.findByTokenHash(hash(token))
                .filter(invite -> invite.isUsableAt(Instant.now()))
                .orElseThrow(InvalidInviteException::new);
    }

    private static List<InvitePreview.ClaimableMember> claimable(ExpenseGroup group) {
        return group.getMembers().stream()
                .filter(m -> !m.hasAccount())
                .map(m -> new InvitePreview.ClaimableMember(m.getId(), m.getName()))
                .toList();
    }

    private AppUser user(long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User " + userId + " does not exist"));
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
