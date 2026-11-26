package com.tally.service.view;

import java.math.BigDecimal;
import java.util.List;

/** A group as shown on the home page. */
public record GroupCard(long id, String name, String description, String currency,
                        List<String> memberNames, int expenseCount, BigDecimal totalSpent) {
}
