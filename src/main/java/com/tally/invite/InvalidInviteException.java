package com.tally.invite;

/** The invite link is unknown, expired or switched off. Rendered as a friendly 404 page. */
public class InvalidInviteException extends RuntimeException {

    public InvalidInviteException() {
        super("This invite link doesn't work any more.");
    }
}
