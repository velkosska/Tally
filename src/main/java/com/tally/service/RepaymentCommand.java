package com.tally.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Input for recording that one member paid another back. */
public record RepaymentCommand(long fromMemberId, long toMemberId, BigDecimal amount, LocalDate date, String note) {
}
