package com.tally.web;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * "just now", "5 min ago", "3 h ago", "yesterday", then a date. Exposed to templates as the
 * {@code when} bean, e.g. {@code ${@when.ago(entry.at)}}. Relative times read the same in every time zone.
 */
@Component("when")
public class TimeAgo {

    private static final DateTimeFormatter THIS_YEAR = DateTimeFormatter.ofPattern("d MMM", Locale.UK);
    private static final DateTimeFormatter OLDER = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

    private final Clock clock;

    public TimeAgo() {
        this(Clock.systemUTC());
    }

    TimeAgo(Clock clock) {
        this.clock = clock;
    }

    public String ago(Instant moment) {
        Instant now = clock.instant();
        Duration elapsed = Duration.between(moment, now);
        if (elapsed.toMinutes() < 1) {
            return "just now";
        }
        if (elapsed.toHours() < 1) {
            return elapsed.toMinutes() + " min ago";
        }
        if (elapsed.toHours() < 24) {
            return elapsed.toHours() + " h ago";
        }
        if (elapsed.toHours() < 48) {
            return "yesterday";
        }
        if (elapsed.toDays() < 7) {
            return elapsed.toDays() + " days ago";
        }
        var date = moment.atZone(ZoneId.of("UTC"));
        return (date.getYear() == now.atZone(ZoneId.of("UTC")).getYear() ? THIS_YEAR : OLDER).format(date);
    }

    /** The exact moment in UTC, for the hover title. */
    public String exact(Instant moment) {
        return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.UK).format(moment.atZone(ZoneId.of("UTC")));
    }
}
