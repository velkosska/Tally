package com.tally.domain;

/** A user someone shares groups with, and in how many groups (see {@link ExpenseGroupRepository#findFriends}). */
public record SharedUser(String displayName, long sharedGroups) {
}
