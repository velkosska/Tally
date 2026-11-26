package com.tally.settlement;

/** How an expense is divided between the people who share it. */
public enum SplitMethod {
    /** Everyone pays the same; leftover cents go to the lowest ids. */
    EQUAL("Equally", "each"),
    /** Each person's exact amount is given; they must add up to the total. */
    EXACT("Exact amounts", "amount"),
    /** Each person pays a percentage; they must add up to 100. */
    PERCENT("Percentages", "%"),
    /** Each person pays in proportion to a number of shares (for example nights stayed). */
    SHARES("Shares", "shares");

    private final String label;
    private final String unit;

    SplitMethod(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public String getLabel() {
        return label;
    }

    /** What the number next to each person means, shown in the form. */
    public String getUnit() {
        return unit;
    }

    /** Whether each person needs a number (amount, percentage or shares), not just a tick. */
    public boolean usesWeights() {
        return this != EQUAL;
    }
}
