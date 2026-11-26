package com.tally.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * Who can reach what. Everything needs a login except the login and sign-up pages, static files,
 * error pages and the health check. Which groups a logged-in user may see is decided in the
 * service layer (see {@code ExpenseGroupRepository#getForUser}), so every URL is covered.
 */
@Configuration
public class SecurityConfig {

    private static final int REMEMBER_ME_SECONDS = (int) Duration.ofDays(30).toSeconds();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TallyUserDetailsService userDetailsService,
                                            PersistentTokenRepository rememberMeTokens,
                                            @Value("${tally.security.remember-me-key}") String rememberMeKey)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/signup", "/error", "/error/**").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/favicon.svg").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/", false)
                        .failureUrl("/login?error"))
                .rememberMe(remember -> remember
                        .tokenRepository(rememberMeTokens)
                        .userDetailsService(userDetailsService)
                        .key(rememberMeKey)
                        .tokenValiditySeconds(REMEMBER_ME_SECONDS))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout"));
        // CSRF protection is on by default; Thymeleaf adds the token to every th:action form.
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Remember-me tokens live in the persistent_logins table, so they survive restarts and can be revoked. */
    @Bean
    PersistentTokenRepository rememberMeTokens(DataSource dataSource) {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }
}
