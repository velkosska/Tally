package com.tally.service.view;

import com.tally.domain.Category;
import com.tally.settlement.SplitMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * An expense as listed on the group page and in the CSV export.
 *
 * @param shares       what each participant pays, in name order; adds up to the amount
 * @param hasReceipt   whether a receipt photo or PDF is attached
 * @param commentCount how many comments the expense has
 */
public record ExpenseRow(long id, String description, BigDecimal amount, Category category,
                         LocalDate date, String paidByName, SplitMethod splitMethod, List<Share> shares,
                         boolean hasReceipt, long commentCount) {

    public List<String> participantNames() {
        return shares.stream().map(Share::name).toList();
    }

    public boolean isEqualSplit() {
        return splitMethod == SplitMethod.EQUAL;
    }

    /** The share of the first participant, which for an equal split is (within a cent) everyone's. */
    public BigDecimal perPerson() {
        return shares.getFirst().amount();
    }

    /** One participant's part of an expense. */
    public record Share(String name, BigDecimal amount) {
    }
}
