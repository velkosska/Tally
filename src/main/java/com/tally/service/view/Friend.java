package com.tally.service.view;

/** Someone with an account who shares at least one group with the current user. */
public record Friend(String name, long sharedGroups) {
}
