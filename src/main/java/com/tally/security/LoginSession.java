package com.tally.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Logs a user in straight after sign-up, so they don't have to type their password twice. */
@Component
public class LoginSession {

    private final TallyUserDetailsService userDetailsService;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public LoginSession(TallyUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    public void logIn(String email, HttpServletRequest request, HttpServletResponse response) {
        TallyUser user = userDetailsService.loadUserByUsername(email);
        if (request.getSession(false) != null) {
            request.changeSessionId();   // a new session id on login prevents session fixation
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
