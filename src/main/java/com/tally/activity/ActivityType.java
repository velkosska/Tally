package com.tally.activity;

/** The kinds of change the activity feed records, with the verb shown after the person's name. */
public enum ActivityType {
    GROUP_CREATED("created the group"),
    GROUP_UPDATED("changed the group details"),
    MEMBER_ADDED("added"),
    MEMBER_REMOVED("removed"),
    MEMBER_JOINED("joined the group as"),
    EXPENSE_ADDED("added"),
    EXPENSE_UPDATED("edited"),
    EXPENSE_DELETED("deleted"),
    PAYMENT_RECORDED("recorded a payment:"),
    PAYMENT_DELETED("deleted a payment:"),
    COMMENT_ADDED("commented on"),
    RECEIPT_ADDED("attached a receipt to"),
    RECEIPT_REPLACED("replaced the receipt of"),
    RECEIPT_REMOVED("removed the receipt of"),
    INVITE_CREATED("created an invite link"),
    INVITES_REVOKED("switched off the invite links");

    private final String verb;

    ActivityType(String verb) {
        this.verb = verb;
    }

    public String getVerb() {
        return verb;
    }

    /** Expenses get their name in quotes; people and payments don't. */
    public boolean quotesSubject() {
        return this == EXPENSE_ADDED || this == EXPENSE_UPDATED || this == EXPENSE_DELETED
                || this == COMMENT_ADDED || this == RECEIPT_ADDED || this == RECEIPT_REPLACED
                || this == RECEIPT_REMOVED;
    }

    /** For the small colored dot next to each entry. */
    public String getTone() {
        return switch (this) {
            case EXPENSE_DELETED, PAYMENT_DELETED, MEMBER_REMOVED, INVITES_REVOKED, RECEIPT_REMOVED -> "removed";
            case PAYMENT_RECORDED -> "paid";
            default -> "added";
        };
    }
}
