package com.tally.service.view;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A payment someone already made to settle up, as listed under "Payments made". */
public record RepaymentRow(long id, String fromName, String toName, BigDecimal amount, LocalDate date,
                           String note) {
}
