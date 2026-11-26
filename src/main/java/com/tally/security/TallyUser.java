package com.tally.security;

import com.tally.account.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The logged-in user as Spring Security sees it. Controllers receive it with
 * {@code @AuthenticationPrincipal TallyUser user} and pass {@link #id()} to the services,
 * which use it for every access check.
 */
public record TallyUser(long id, String email, String displayName, String passwordHash) implements UserDetails {

    public static TallyUser from(AppUser user) {
        return new TallyUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getPasswordHash());
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Used by templates: {@code ${#authentication.principal.displayName}}. */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String toString() {
        return "TallyUser[id=" + id + "]";   // never print the password hash
    }
}
