package com.tally.invite;

import java.util.List;

/**
 * What someone sees after opening an invite link: the group, and the names added before anyone had an
 * account, one of which may be them.
 */
public record InvitePreview(long groupId, String groupName, String groupDescription, List<String> memberNames,
                            List<ClaimableMember> claimable, String suggestedName) {

    /** A member who was added by name and hasn't joined with an account yet. */
    public record ClaimableMember(long id, String name) {
    }
}
