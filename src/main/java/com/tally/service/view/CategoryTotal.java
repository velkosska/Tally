package com.tally.service.view;

import java.math.BigDecimal;

/** Total spent in one category; also serialized to JSON for the chart. */
public record CategoryTotal(String label, String color, BigDecimal amount, int percent) {
}
