package com.tally.web;

import com.tally.account.AccountService;
import com.tally.domain.BusinessRuleException;
import com.tally.security.LoginSession;
import com.tally.web.form.SignupForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Login and sign-up pages. Spring Security itself handles POST /login and POST /logout. */
@Controller
public class AuthController {

    private final AccountService accountService;
    private final LoginSession loginSession;

    public AuthController(AccountService accountService, LoginSession loginSession) {
        this.accountService = accountService;
        this.loginSession = loginSession;
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        return isLoggedIn(authentication) ? "redirect:/" : "auth/login";
    }

    @GetMapping("/signup")
    public String signupForm(Authentication authentication, Model model) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("signupForm", new SignupForm());
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("signupForm") SignupForm form, BindingResult errors,
                         HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return "auth/signup";
        }
        try {
            accountService.signUp(form.getEmail(), form.getDisplayName(), form.getPassword());
        } catch (BusinessRuleException e) {
            errors.rejectValue("email", "taken", e.getMessage());
            return "auth/signup";
        }
        loginSession.logIn(form.getEmail(), request, response);
        Flash.success(redirect, "Welcome to Tally, " + form.getDisplayName().strip() + "!");
        return "redirect:/";
    }

    private static boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
