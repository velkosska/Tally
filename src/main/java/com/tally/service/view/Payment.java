package com.tally.service.view;

import java.math.BigDecimal;

/** One step of the settle-up plan, with names ready for display and ids for "Mark as paid". */
public record Payment(long fromId, String fromName, long toId, String toName, BigDecimal amount) {
}
