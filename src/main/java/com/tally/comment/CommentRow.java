package com.tally.comment;

import java.time.Instant;

/** A comment as shown under an expense. Only its author can delete it. */
public record CommentRow(long id, String authorName, boolean byYou, String body, Instant at) {

    public String who() {
        return byYou ? "You" : authorName;
    }
}
