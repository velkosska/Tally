package com.tally.web;

import com.tally.service.DashboardService;
import com.tally.service.GroupService;
import com.tally.web.form.GroupForm;
import com.tally.web.form.MemberForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class GroupController {

    private final GroupService groupService;
    private final DashboardService dashboardService;

    public GroupController(GroupService groupService, DashboardService dashboardService) {
        this.groupService = groupService;
        this.dashboardService = dashboardService;
    }

    @ModelAttribute("currencies")
    List<String> currencies() {
        return GroupForm.CURRENCIES;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("groups", groupService.listGroups());
        return "groups/index";
    }

    @GetMapping("/groups/new")
    public String newGroup(Model model) {
        model.addAttribute("groupForm", new GroupForm());
        return "groups/form";
    }

    @PostMapping("/groups")
    public String create(@Valid @ModelAttribute("groupForm") GroupForm form, BindingResult errors,
                         RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return "groups/form";
        }
        long id = groupService.create(form.toCommand());
        Flash.success(redirect, "Group created. Now add the people who share costs.");
        return "redirect:/groups/" + id;
    }

    @GetMapping("/groups/{groupId}")
    public String show(@PathVariable long groupId, Model model) {
        model.addAttribute("group", dashboardService.dashboard(groupId));
        if (!model.containsAttribute("memberForm")) {
            model.addAttribute("memberForm", new MemberForm());
        }
        return "groups/detail";
    }

    @GetMapping("/groups/{groupId}/edit")
    public String edit(@PathVariable long groupId, Model model) {
        model.addAttribute("groupId", groupId);
        model.addAttribute("groupForm", GroupForm.from(groupService.details(groupId)));
        return "groups/form";
    }

    @PostMapping("/groups/{groupId}")
    public String update(@PathVariable long groupId, @Valid @ModelAttribute("groupForm") GroupForm form,
                         BindingResult errors, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            model.addAttribute("groupId", groupId);
            return "groups/form";
        }
        groupService.update(groupId, form.toCommand());
        Flash.success(redirect, "Group updated.");
        return "redirect:/groups/" + groupId;
    }

    @PostMapping("/groups/{groupId}/delete")
    public String delete(@PathVariable long groupId, RedirectAttributes redirect) {
        groupService.delete(groupId);
        Flash.success(redirect, "Group deleted.");
        return "redirect:/";
    }
}
