package com.tally.service.view;

/**
 * A member as shown in lists and form pickers.
 *
 * @param joined whether the member is linked to an account (joined themselves or created the group)
 * @param you    whether that account is the person looking at the page
 */
public record MemberOption(long id, String name, boolean removable, boolean joined, boolean you) {

    /** A plain option for pickers, where only the id and name matter. */
    public MemberOption(long id, String name) {
        this(id, name, false, false, false);
    }
}
