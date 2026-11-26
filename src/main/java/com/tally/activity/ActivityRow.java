package com.tally.activity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An activity entry ready for display: "You added “Dinner” €90.00 · paid by Ana".
 *
 * @param byYou whether the person looking at the page made the change, so it can say "You"
 */
public record ActivityRow(long groupId, String groupName, String currency, String actorName, boolean byYou,
                          ActivityType type, String subject, BigDecimal amount, String details, Instant at) {

    public String who() {
        return byYou ? "You" : actorName;
    }
}
