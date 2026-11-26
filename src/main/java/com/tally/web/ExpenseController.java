package com.tally.web;

import com.tally.domain.BusinessRuleException;
import com.tally.domain.Category;
import com.tally.service.ExpenseService;
import com.tally.service.view.ExpenseFormPage;
import com.tally.web.form.ExpenseForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/groups/{groupId}/expenses")
public class ExpenseController {

    private static final String FORM_VIEW = "expenses/form";

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @ModelAttribute("page")
    ExpenseFormPage page(@PathVariable long groupId) {
        return expenseService.formPage(groupId);
    }

    @ModelAttribute("categories")
    Category[] categories() {
        return Category.values();
    }

    @GetMapping("/new")
    public String newExpense(@ModelAttribute("page") ExpenseFormPage page, Model model,
                             RedirectAttributes redirect) {
        if (page.members().isEmpty()) {
            Flash.error(redirect, "Add at least one member before adding expenses.");
            return "redirect:/groups/" + page.groupId() + "#members";
        }
        model.addAttribute("expenseForm", ExpenseForm.defaults(page.members()));
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@PathVariable long groupId, @Valid @ModelAttribute("expenseForm") ExpenseForm form,
                         BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            expenseService.create(groupId, form.toCommand());
        } catch (BusinessRuleException e) {
            errors.reject("businessRule", e.getMessage());
            return FORM_VIEW;
        }
        Flash.success(redirect, "Expense added.");
        return "redirect:/groups/" + groupId;
    }

    @GetMapping("/{expenseId}/edit")
    public String edit(@PathVariable long groupId, @PathVariable long expenseId, Model model) {
        model.addAttribute("expenseId", expenseId);
        model.addAttribute("expenseForm", ExpenseForm.from(expenseService.details(groupId, expenseId)));
        return FORM_VIEW;
    }

    @PostMapping("/{expenseId}")
    public String update(@PathVariable long groupId, @PathVariable long expenseId,
                         @Valid @ModelAttribute("expenseForm") ExpenseForm form, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        model.addAttribute("expenseId", expenseId);
        if (errors.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            expenseService.update(groupId, expenseId, form.toCommand());
        } catch (BusinessRuleException e) {
            errors.reject("businessRule", e.getMessage());
            return FORM_VIEW;
        }
        Flash.success(redirect, "Expense updated.");
        return "redirect:/groups/" + groupId;
    }

    @PostMapping("/{expenseId}/delete")
    public String delete(@PathVariable long groupId, @PathVariable long expenseId, RedirectAttributes redirect) {
        expenseService.delete(groupId, expenseId);
        Flash.success(redirect, "Expense deleted.");
        return "redirect:/groups/" + groupId;
    }
}
