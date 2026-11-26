package com.tally.settlement;

import java.math.BigDecimal;

/** A single payment that moves money from a debtor to a creditor. */
public record Transfer(long fromId, long toId, BigDecimal amount) {
}
