package com.tally.web.form;

import com.tally.domain.Category;
import com.tally.service.ExpenseCommand;
import com.tally.service.view.MemberOption;
import com.tally.settlement.SplitMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Backing object for the add/edit expense form. */
public class ExpenseForm {

    @NotBlank(message = "What was it for?")
    @Size(max = 120, message = "Keep the description under 120 characters.")
    private String description;

    @NotNull(message = "Enter an amount.")
    @DecimalMin(value = "0.01", message = "The amount must be at least 0.01.")
    @Digits(integer = 10, fraction = 2, message = "Use at most two decimals.")
    private BigDecimal amount;

    @NotNull(message = "Pick a category.")
    private Category category = Category.FOOD;

    @NotNull(message = "Pick a date.")
    @PastOrPresent(message = "The date can't be in the future.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date = LocalDate.now();

    @NotNull(message = "Who paid?")
    private Long paidById;

    @NotEmpty(message = "Pick at least one person to split with.")
    private List<Long> participantIds = new ArrayList<>();

    @NotNull(message = "Pick how to split it.")
    private SplitMethod splitMethod = SplitMethod.EQUAL;

    /** Member id to their amount, percentage or shares; only used for an uneven split. */
    private Map<Long, BigDecimal> weights = new HashMap<>();

    /** A fresh form: today's date, first member paying, split between everyone. */
    public static ExpenseForm defaults(List<MemberOption> members) {
        ExpenseForm form = new ExpenseForm();
        if (!members.isEmpty()) {
            form.setPaidById(members.getFirst().id());
        }
        form.setParticipantIds(new ArrayList<>(members.stream().map(MemberOption::id).toList()));
        return form;
    }

    public static ExpenseForm from(ExpenseCommand command) {
        ExpenseForm form = new ExpenseForm();
        form.setDescription(command.description());
        form.setAmount(command.amount());
        form.setCategory(command.category());
        form.setDate(command.date());
        form.setPaidById(command.paidById());
        form.setParticipantIds(new ArrayList<>(command.participantIds()));
        form.setSplitMethod(command.splitMethod());
        Map<Long, BigDecimal> weights = new HashMap<>();
        // "3" shares and "60" % read better than "3.00" and "60.00"; amounts keep their cents.
        command.weights().forEach((id, weight) -> weights.put(id,
                command.splitMethod() == SplitMethod.EXACT ? weight : weight.stripTrailingZeros()));
        form.setWeights(weights);
        return form;
    }

    public ExpenseCommand toCommand() {
        Map<Long, BigDecimal> chosenWeights = new HashMap<>();
        if (splitMethod != null && splitMethod.usesWeights()) {
            participantIds.forEach(id -> {
                BigDecimal weight = weights.get(id);
                if (weight != null) {
                    chosenWeights.put(id, weight);
                }
            });
        }
        return new ExpenseCommand(description, amount, category, date, paidById, List.copyOf(participantIds),
                splitMethod, chosenWeights);
    }

    public SplitMethod getSplitMethod() {
        return splitMethod;
    }

    public void setSplitMethod(SplitMethod splitMethod) {
        this.splitMethod = splitMethod;
    }

    public Map<Long, BigDecimal> getWeights() {
        return weights;
    }

    public void setWeights(Map<Long, BigDecimal> weights) {
        this.weights = weights == null ? new HashMap<>() : weights;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Long getPaidById() {
        return paidById;
    }

    public void setPaidById(Long paidById) {
        this.paidById = paidById;
    }

    public List<Long> getParticipantIds() {
        return participantIds;
    }

    public void setParticipantIds(List<Long> participantIds) {
        this.participantIds = participantIds == null ? new ArrayList<>() : participantIds;
    }
}
