package com.tally.web;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TimeAgoTest {

    private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");
    private final TimeAgo when = new TimeAgo(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void recentTimesAreRelative() {
        assertThat(when.ago(NOW.minusSeconds(20))).isEqualTo("just now");
        assertThat(when.ago(NOW.minus(Duration.ofMinutes(5)))).isEqualTo("5 min ago");
        assertThat(when.ago(NOW.minus(Duration.ofHours(3)))).isEqualTo("3 h ago");
        assertThat(when.ago(NOW.minus(Duration.ofHours(30)))).isEqualTo("yesterday");
        assertThat(when.ago(NOW.minus(Duration.ofDays(4)))).isEqualTo("4 days ago");
    }

    @Test
    void olderTimesShowTheDate() {
        assertThat(when.ago(Instant.parse("2026-11-02T09:00:00Z"))).isEqualTo("2 Nov");
        assertThat(when.ago(Instant.parse("2025-12-24T09:00:00Z"))).isEqualTo("24 Dec 2025");
        assertThat(when.exact(Instant.parse("2025-12-24T09:05:00Z"))).isEqualTo("24 Dec 2025, 09:05 UTC");
    }
}
