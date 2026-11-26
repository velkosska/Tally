package com.tally.security;

import com.tally.account.AppUser;
import com.tally.account.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads users by email for the login form and for remember-me cookies. */
@Service
public class TallyUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public TallyUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public TallyUser loadUserByUsername(String email) {
        return users.findByEmail(AppUser.normalizeEmail(email))
                .map(TallyUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("No account for this email"));
    }
}
