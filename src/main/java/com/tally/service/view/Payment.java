package com.tally.service.view;

import java.math.BigDecimal;

/** One step of the settle-up plan, with names ready for display. */
public record Payment(String fromName, String toName, BigDecimal amount) {
}
