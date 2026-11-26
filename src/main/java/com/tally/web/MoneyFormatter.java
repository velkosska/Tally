package com.tally.web;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/**
 * Formats amounts for display. Exposed to templates as the {@code money} bean,
 * e.g. {@code ${@money.format(total, currency)}}.
 */
@Component("money")
public class MoneyFormatter {

    private static final Locale DISPLAY_LOCALE = Locale.UK;

    public String format(BigDecimal amount, String currencyCode) {
        NumberFormat format = NumberFormat.getCurrencyInstance(DISPLAY_LOCALE);
        format.setCurrency(Currency.getInstance(currencyCode));
        return format.format(amount);
    }

    public String symbol(String currencyCode) {
        return Currency.getInstance(currencyCode).getSymbol(DISPLAY_LOCALE);
    }
}
