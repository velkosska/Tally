package com.tally.activity;

import com.tally.domain.Expense;
import com.tally.domain.Member;
import com.tally.settlement.SplitMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** The parts of an expense people care about, so an edit can be described as "amount 80.00 → 90.00". */
public record ExpenseSnapshot(String description, BigDecimal amount, String category, String date,
                              String paidBy, String split) {

    public static ExpenseSnapshot of(Expense expense) {
        String people = expense.getParticipants().stream().map(Member::getName).collect(Collectors.joining(", "));
        String split = expense.getSplitMethod() == SplitMethod.EQUAL
                ? "equally between " + people
                : "by " + expense.getSplitMethod().getLabel().toLowerCase() + " between " + people;
        return new ExpenseSnapshot(expense.getDescription(), expense.getAmount(), expense.getCategory().getLabel(),
                expense.getDate().toString(), expense.getPaidBy().getName(), split);
    }

    /** "paid by Ana, split equally between Ana, Ben" for a new expense. */
    public String summary() {
        return "paid by " + paidBy + ", split " + split;
    }

    /** What changed between this version and the next, or "no changes". */
    public String changesTo(ExpenseSnapshot after) {
        List<String> changes = new ArrayList<>();
        change(changes, "name", description, after.description);
        if (amount.compareTo(after.amount) != 0) {
            changes.add("amount " + money(amount) + " → " + money(after.amount));
        }
        change(changes, "category", category, after.category);
        change(changes, "date", date, after.date);
        change(changes, "paid by", paidBy, after.paidBy);
        if (!Objects.equals(split, after.split)) {
            changes.add("now split " + after.split);
        }
        return changes.isEmpty() ? "no changes" : String.join("; ", changes);
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void change(List<String> changes, String what, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(what + " " + before + " → " + after);
        }
    }
}
