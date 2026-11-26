package com.tally.service.view;

import com.tally.domain.Category;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** An expense as listed on the group page and in the CSV export. */
public record ExpenseRow(long id, String description, BigDecimal amount, Category category,
                         LocalDate date, String paidByName, List<String> participantNames,
                         BigDecimal perPerson) {
}
