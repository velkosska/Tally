package com.tally.service;

import com.tally.domain.Category;
import com.tally.settlement.SplitMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Input for creating or editing an expense.
 *
 * @param weights participant id to their amount, percentage or shares; only read for an uneven split
 */
public record ExpenseCommand(String description, BigDecimal amount, Category category, LocalDate date,
                             long paidById, List<Long> participantIds,
                             SplitMethod splitMethod, Map<Long, BigDecimal> weights) {

    public ExpenseCommand {
        splitMethod = splitMethod == null ? SplitMethod.EQUAL : splitMethod;
        weights = weights == null ? Map.of() : weights;
    }

    /** An expense split equally between the participants. */
    public ExpenseCommand(String description, BigDecimal amount, Category category, LocalDate date,
                          long paidById, List<Long> participantIds) {
        this(description, amount, category, date, paidById, participantIds, SplitMethod.EQUAL, Map.of());
    }
}
