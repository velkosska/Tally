package com.tally.web.form;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Backing object for the sign-up form. */
public class SignupForm {

    @NotBlank(message = "Tell us what to call you.")
    @Size(max = 50, message = "Keep your name under 50 characters.")
    private String displayName;

    @NotBlank(message = "Enter your email address.")
    @Email(message = "That doesn't look like an email address.")
    @Size(max = 254, message = "That email address is too long.")
    private String email;

    /** BCrypt only uses the first 72 bytes of a password, so longer ones are refused rather than cut. */
    @NotBlank(message = "Choose a password.")
    @Size(min = 8, max = 72, message = "Use between 8 and 72 characters.")
    private String password;

    private String confirmPassword;

    @AssertTrue(message = "The two passwords don't match.")
    public boolean isPasswordConfirmed() {
        return password == null || password.equals(confirmPassword);
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    /** Stray spaces from copy-pasting an address shouldn't fail validation. */
    public void setEmail(String email) {
        this.email = email == null ? null : email.strip();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
