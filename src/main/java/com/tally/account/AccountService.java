package com.tally.account;

import com.tally.domain.BusinessRuleException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating accounts. Passwords are only ever stored as BCrypt hashes. */
@Service
@Transactional
public class AccountService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /** Creates an account and returns its id. Throws if the email is already registered. */
    public long signUp(String email, String displayName, String rawPassword) {
        String normalized = AppUser.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new BusinessRuleException("There is already an account with this email. Try logging in.");
        }
        return users.saveAndFlush(new AppUser(normalized, displayName, passwordEncoder.encode(rawPassword))).getId();
    }
}
