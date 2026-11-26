package com.tally.web;

import com.tally.invite.InviteService;
import com.tally.service.DashboardService;
import com.tally.service.GroupService;
import com.tally.web.form.GroupForm;
import com.tally.web.form.MemberForm;
import com.tally.security.TallyUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final InviteService inviteService;

    public GroupController(GroupService groupService, DashboardService dashboardService,
                           InviteService inviteService) {
        this.groupService = groupService;
        this.dashboardService = dashboardService;
        this.inviteService = inviteService;
    }

    @ModelAttribute("currencies")
    List<String> currencies() {
        return GroupForm.CURRENCIES;
    }

    @GetMapping("/")
    public String index(@AuthenticationPrincipal TallyUser user, Model model) {
        model.addAttribute("groups", groupService.listGroups(user.id()));
        model.addAttribute("friends", groupService.listFriends(user.id()));
        return "groups/index";
    }

    @GetMapping("/groups/new")
    public String newGroup(Model model) {
        model.addAttribute("groupForm", new GroupForm());
        return "groups/form";
    }

    @PostMapping("/groups")
    public String create(@AuthenticationPrincipal TallyUser user,
                         @Valid @ModelAttribute("groupForm") GroupForm form, BindingResult errors,
                         RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return "groups/form";
        }
        long id = groupService.create(user.id(), form.toCommand());
        Flash.success(redirect, "Group created. Now add the people you share costs with.");
        return "redirect:/groups/" + id;
    }

    @GetMapping("/groups/{groupId}")
    public String show(@AuthenticationPrincipal TallyUser user, @PathVariable long groupId, Model model) {
        model.addAttribute("group", dashboardService.dashboard(user.id(), groupId));
        model.addAttribute("activeInviteLinks", inviteService.countActiveLinks(user.id(), groupId));
        if (!model.containsAttribute("memberForm")) {
            model.addAttribute("memberForm", new MemberForm());
        }
        return "groups/detail";
    }

    @GetMapping("/groups/{groupId}/edit")
    public String edit(@AuthenticationPrincipal TallyUser user, @PathVariable long groupId, Model model) {
        model.addAttribute("groupId", groupId);
        model.addAttribute("groupForm", GroupForm.from(groupService.details(user.id(), groupId)));
        return "groups/form";
    }

    @PostMapping("/groups/{groupId}")
    public String update(@AuthenticationPrincipal TallyUser user,
                         @PathVariable long groupId, @Valid @ModelAttribute("groupForm") GroupForm form,
                         BindingResult errors, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            model.addAttribute("groupId", groupId);
            return "groups/form";
        }
        groupService.update(user.id(), groupId, form.toCommand());
        Flash.success(redirect, "Group updated.");
        return "redirect:/groups/" + groupId;
    }

    @PostMapping("/groups/{groupId}/delete")
    public String delete(@AuthenticationPrincipal TallyUser user,
                         @PathVariable long groupId, RedirectAttributes redirect) {
        groupService.delete(user.id(), groupId);
        Flash.success(redirect, "Group deleted.");
        return "redirect:/";
    }
}
