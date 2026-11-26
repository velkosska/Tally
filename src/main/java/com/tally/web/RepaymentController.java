package com.tally.web;

import com.tally.domain.BusinessRuleException;
import com.tally.security.TallyUser;
import com.tally.service.RepaymentService;
import com.tally.web.form.RepaymentForm;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Payments members make to each other: "Mark as paid" on the settle-up plan, or recorded by hand. */
@Controller
@RequestMapping("/groups/{groupId}/repayments")
public class RepaymentController {

    private final RepaymentService repaymentService;

    public RepaymentController(RepaymentService repaymentService) {
        this.repaymentService = repaymentService;
    }

    @PostMapping
    public String record(@AuthenticationPrincipal TallyUser user, @PathVariable long groupId,
                         @Valid @ModelAttribute RepaymentForm form, BindingResult errors,
                         RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            Flash.error(redirect, errors.getAllErrors().getFirst().getDefaultMessage());
            return "redirect:/groups/" + groupId + "#payments";
        }
        try {
            repaymentService.record(user.id(), groupId, form.toCommand());
            Flash.success(redirect, "Payment recorded. Balances are updated.");
        } catch (BusinessRuleException e) {
            Flash.error(redirect, e.getMessage());
        }
        return "redirect:/groups/" + groupId + "#payments";
    }

    @PostMapping("/{repaymentId}/delete")
    public String delete(@AuthenticationPrincipal TallyUser user, @PathVariable long groupId,
                         @PathVariable long repaymentId, RedirectAttributes redirect) {
        repaymentService.delete(user.id(), groupId, repaymentId);
        Flash.success(redirect, "Payment deleted. Balances are updated.");
        return "redirect:/groups/" + groupId + "#payments";
    }
}
