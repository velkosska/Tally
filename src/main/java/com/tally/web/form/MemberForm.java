package com.tally.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Backing object for the inline "add member" form. */
public class MemberForm {

    @NotBlank(message = "Type a name to add a member.")
    @Size(max = 50, message = "Keep names under 50 characters.")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
