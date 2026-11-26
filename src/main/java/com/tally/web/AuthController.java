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
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

/** Login and sign-up pages. Spring Security itself handles POST /login and POST /logout. */
@Controller
public class AuthController {

    private final AccountService accountService;
    private final LoginSession loginSession;
    /** Where Spring Security remembers the page someone wanted before it sent them to log in. */
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public AuthController(AccountService accountService, LoginSession loginSession) {
        this.accountService = accountService;
        this.loginSession = loginSession;
    }

    @GetMapping("/login")
    public String login(Authentication authentication, HttpServletRequest request, HttpServletResponse response,
                        Model model) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("joiningGroup", openedInviteLink(request, response));
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signupForm(Authentication authentication, HttpServletRequest request,
                             HttpServletResponse response, Model model) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("signupForm", new SignupForm());
        model.addAttribute("joiningGroup", openedInviteLink(request, response));
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("signupForm") SignupForm form, BindingResult errors,
                         HttpServletRequest request, HttpServletResponse response, Model model,
                         RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            model.addAttribute("joiningGroup", openedInviteLink(request, response));
            return "auth/signup";
        }
        try {
            accountService.signUp(form.getEmail(), form.getDisplayName(), form.getPassword());
        } catch (BusinessRuleException e) {
            errors.rejectValue("email", "taken", e.getMessage());
            model.addAttribute("joiningGroup", openedInviteLink(request, response));
            return "auth/signup";
        }
        boolean joining = openedInviteLink(request, response);
        SavedRequest saved = requestCache.getRequest(request, response);
        loginSession.logIn(form.getEmail(), request, response);
        Flash.success(redirect, "Welcome to Tally, " + form.getDisplayName().strip() + "!");
        if (joining) {
            // Back to the invite link they opened before signing up.
            requestCache.removeRequest(request, response);
            return "redirect:" + saved.getRedirectUrl();
        }
        return "redirect:/";
    }

    /** True when the visitor was sent here after opening an invite link, so the page can say so. */
    private boolean openedInviteLink(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requestCache.getRequest(request, response);
        return saved != null && "GET".equals(saved.getMethod())
                && URI.create(saved.getRedirectUrl()).getPath().startsWith(request.getContextPath() + "/invites/");
    }

    private static boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
