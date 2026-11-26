package com.tally.web;

import com.tally.TestcontainersConfiguration;
import com.tally.account.AccountService;
import com.tally.account.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Sign up, log in, remember me, log out and CSRF, through the real Spring Security filter chain. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    AccountService accountService;
    @Autowired
    AppUserRepository users;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void pagesNeedALogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/groups/1/export.csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void loginAndSignupPagesAndHealthArePublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("Log in")));
        mvc.perform(get("/signup")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Create an account")));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    @Test
    void signingUpCreatesAnAccountAndLogsYouIn() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                        .param("displayName", "Ana")
                        .param("email", " Ana@Example.com ")
                        .param("password", "correct horse battery")
                        .param("confirmPassword", "correct horse battery"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("ana@example.com"));

        var saved = users.findByEmail("ana@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2").doesNotContain("correct horse battery");
        assertThat(passwordEncoder.matches("correct horse battery", saved.getPasswordHash())).isTrue();
    }

    @Test
    void signupValidatesTheForm() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                        .param("displayName", "")
                        .param("email", "not-an-email")
                        .param("password", "short")
                        .param("confirmPassword", "different"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm",
                        "displayName", "email", "password", "passwordConfirmed"))
                .andExpect(unauthenticated());
    }

    @Test
    void signupRefusesAnEmailThatIsAlreadyUsed() throws Exception {
        accountService.signUp("ana@example.com", "Ana", "correct horse battery");

        mvc.perform(post("/signup").with(csrf())
                        .param("displayName", "Someone else")
                        .param("email", "ANA@example.com")
                        .param("password", "another password")
                        .param("confirmPassword", "another password"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupForm", "email"))
                .andExpect(content().string(containsString("already an account")));
    }

    @Test
    void logInWithTheRightPassword() throws Exception {
        accountService.signUp("ana@example.com", "Ana", "correct horse battery");

        mvc.perform(formLogin("/login").userParameter("email").user("ANA@example.com").password("correct horse battery"))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("ana@example.com"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        accountService.signUp("ana@example.com", "Ana", "correct horse battery");

        mvc.perform(formLogin("/login").userParameter("email").user("ana@example.com").password("wrong password"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void rememberMeSetsALongLivedCookie() throws Exception {
        accountService.signUp("ana@example.com", "Ana", "correct horse battery");

        mvc.perform(post("/login").with(csrf())
                        .param("email", "ana@example.com")
                        .param("password", "correct horse battery")
                        .param("remember-me", "on"))
                .andExpect(authenticated())
                .andExpect(cookie().exists("remember-me"))
                .andExpect(cookie().maxAge("remember-me", 30 * 24 * 60 * 60))
                .andExpect(cookie().httpOnly("remember-me", true));
    }

    @Test
    void postsWithoutACsrfTokenAreRefused() throws Exception {
        mvc.perform(post("/signup")
                        .param("displayName", "Ana")
                        .param("email", "ana@example.com")
                        .param("password", "correct horse battery")
                        .param("confirmPassword", "correct horse battery"))
                .andExpect(status().isForbidden());
        assertThat(users.findByEmail("ana@example.com")).isEmpty();
    }

    @Test
    void logOutEndsTheSession() throws Exception {
        mvc.perform(logout("/logout"))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());
    }
}
