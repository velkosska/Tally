package com.tally.web.form;

import com.tally.service.GroupCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Backing object for the create/edit group form. */
public class GroupForm {

    public static final List<String> CURRENCIES =
            List.of("EUR", "USD", "GBP", "CHF", "SEK", "NOK", "DKK", "PLN", "CAD", "AUD");

    @NotBlank(message = "Give your group a name.")
    @Size(max = 80, message = "Keep the name under 80 characters.")
    private String name;

    @Size(max = 255, message = "Keep the description under 255 characters.")
    private String description;

    @NotBlank(message = "Pick a currency.")
    @Pattern(regexp = "[A-Z]{3}", message = "Pick a currency from the list.")
    private String currency = "EUR";

    public static GroupForm from(GroupCommand command) {
        GroupForm form = new GroupForm();
        form.setName(command.name());
        form.setDescription(command.description());
        form.setCurrency(command.currency());
        return form;
    }

    public GroupCommand toCommand() {
        return new GroupCommand(name, description, currency);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
