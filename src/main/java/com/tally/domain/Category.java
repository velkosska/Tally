package com.tally.domain;

/** Spending categories, each with a display label and a chart color. */
public enum Category {
    FOOD("Food & drinks", "#f97316"),
    GROCERIES("Groceries", "#84cc16"),
    TRANSPORT("Transport", "#3b82f6"),
    ACCOMMODATION("Accommodation", "#8b5cf6"),
    ENTERTAINMENT("Entertainment", "#ec4899"),
    UTILITIES("Bills & utilities", "#14b8a6"),
    OTHER("Other", "#94a3b8");

    private final String label;
    private final String color;

    Category(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }
}
