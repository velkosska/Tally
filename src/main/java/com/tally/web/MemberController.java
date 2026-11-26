package com.tally.web;

import com.tally.domain.BusinessRuleException;
import com.tally.service.GroupService;
import com.tally.web.form.MemberForm;
import com.tally.security.TallyUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/groups/{groupId}/members")
public class MemberController {

    private final GroupService groupService;

    public MemberController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public String add(@AuthenticationPrincipal TallyUser user,
                      @PathVariable long groupId, @Valid @ModelAttribute MemberForm form,
                      BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            Flash.error(redirect, errors.getFieldError("name").getDefaultMessage());
        } else {
            try {
                groupService.addMember(user.id(), groupId, form.getName());
                Flash.success(redirect, form.getName().strip() + " joined the group.");
            } catch (BusinessRuleException e) {
                Flash.error(redirect, e.getMessage());
            }
        }
        return "redirect:/groups/" + groupId + "#members";
    }

    @PostMapping("/{memberId}/delete")
    public String remove(@AuthenticationPrincipal TallyUser user,
                         @PathVariable long groupId, @PathVariable long memberId, RedirectAttributes redirect) {
        try {
            groupService.removeMember(user.id(), groupId, memberId);
            Flash.success(redirect, "Member removed.");
        } catch (BusinessRuleException e) {
            Flash.error(redirect, e.getMessage());
        }
        return "redirect:/groups/" + groupId + "#members";
    }
}
