package com.tally.web.form;

import com.tally.service.RepaymentCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Backing object for "Record a payment" and the "Mark as paid" buttons on the settle-up plan. */
public class RepaymentForm {

    @NotNull(message = "Who paid?")
    private Long fromMemberId;

    @NotNull(message = "Who got the money?")
    private Long toMemberId;

    @NotNull(message = "Enter an amount.")
    @DecimalMin(value = "0.01", message = "The amount must be at least 0.01.")
    @Digits(integer = 10, fraction = 2, message = "Use at most two decimals.")
    private BigDecimal amount;

    @NotNull(message = "Pick a date.")
    @PastOrPresent(message = "The date can't be in the future.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date = LocalDate.now();

    @Size(max = 120, message = "Keep the note under 120 characters.")
    private String note;

    public RepaymentCommand toCommand() {
        return new RepaymentCommand(fromMemberId, toMemberId, amount, date, note);
    }

    public Long getFromMemberId() {
        return fromMemberId;
    }

    public void setFromMemberId(Long fromMemberId) {
        this.fromMemberId = fromMemberId;
    }

    public Long getToMemberId() {
        return toMemberId;
    }

    public void setToMemberId(Long toMemberId) {
        this.toMemberId = toMemberId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
