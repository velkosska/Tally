package com.tally.web;

import com.tally.service.view.ExpenseRow;
import com.tally.service.view.GroupDashboard;
import com.tally.service.view.MemberBalance;
import com.tally.service.view.Payment;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Writes a group's expenses, balances and settle-up plan as a CSV file that opens in Excel. */
@Component
public class CsvExporter {

    private static final String NEWLINE = "\r\n";

    public String export(GroupDashboard group) {
        StringBuilder csv = new StringBuilder();
        row(csv, "Expenses (" + group.currency() + ")");
        row(csv, "Date", "Description", "Category", "Amount", "Paid by", "Split between");
        for (ExpenseRow expense : group.expenses()) {
            row(csv, expense.date().toString(), expense.description(), expense.category().getLabel(),
                    expense.amount().toPlainString(), expense.paidByName(),
                    String.join("; ", expense.participantNames()));
        }

        csv.append(NEWLINE);
        row(csv, "Balances");
        row(csv, "Member", "Paid", "Share", "Balance");
        for (MemberBalance balance : group.balances()) {
            row(csv, balance.name(), balance.paid().toPlainString(), balance.share().toPlainString(),
                    balance.net().toPlainString());
        }

        csv.append(NEWLINE);
        row(csv, "Settle up");
        row(csv, "From", "To", "Amount");
        for (Payment payment : group.settlement()) {
            row(csv, payment.fromName(), payment.toName(), payment.amount().toPlainString());
        }
        return csv.toString();
    }

    private static void row(StringBuilder csv, String... cells) {
        csv.append(Stream.of(cells).map(CsvExporter::escape).collect(Collectors.joining(","))).append(NEWLINE);
    }

    /**
     * Quotes a cell when needed. Text that starts with =, +, - or @ is prefixed with an apostrophe
     * so a spreadsheet will not run it as a formula (CSV injection).
     */
    static String escape(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !isNumber(text)) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static boolean isNumber(String text) {
        return text.matches("-?\\d+(\\.\\d+)?");
    }
}
