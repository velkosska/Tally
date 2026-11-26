package com.tally.service;

import com.tally.domain.Category;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Input for creating or editing an expense. */
public record ExpenseCommand(String description, BigDecimal amount, Category category, LocalDate date,
                             long paidById, List<Long> participantIds) {
}
