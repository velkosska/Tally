package com.tally.web;

import com.tally.domain.BusinessRuleException;
import com.tally.invite.InviteService;
import com.tally.security.TallyUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Invite links. Members create a link on the group page and share it themselves; whoever opens it
 * logs in (or signs up) and then joins the group. Opening a link needs a login, so Spring Security
 * sends visitors to the login page first and brings them back here afterwards.
 */
@Controller
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping("/groups/{groupId}/invites")
    public String create(@AuthenticationPrincipal TallyUser user, @PathVariable long groupId,
                         RedirectAttributes redirect) {
        String token = inviteService.createLink(user.id(), groupId);
        String link = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/invites/{token}").buildAndExpand(token).toUriString();
        redirect.addFlashAttribute("inviteLink", link);
        return "redirect:/groups/" + groupId + "#invite";
    }

    @PostMapping("/groups/{groupId}/invites/revoke")
    public String revoke(@AuthenticationPrincipal TallyUser user, @PathVariable long groupId,
                         RedirectAttributes redirect) {
        inviteService.revokeAll(user.id(), groupId);
        Flash.success(redirect, "Invite links switched off. Links you shared before no longer work.");
        return "redirect:/groups/" + groupId + "#invite";
    }

    @GetMapping("/invites/{token}")
    public String show(@AuthenticationPrincipal TallyUser user, @PathVariable String token, Model model,
                       RedirectAttributes redirect) {
        var preview = inviteService.preview(token, user.id());
        if (inviteService.isMember(token, user.id())) {
            Flash.success(redirect, "You're already in " + preview.groupName() + ".");
            return "redirect:/groups/" + preview.groupId();
        }
        model.addAttribute("invite", preview);
        model.addAttribute("token", token);
        return "invites/join";
    }

    @PostMapping("/invites/{token}")
    public String accept(@AuthenticationPrincipal TallyUser user, @PathVariable String token,
                         @RequestParam(name = "member", required = false) Long memberId,
                         @RequestParam(name = "name", required = false) String name,
                         RedirectAttributes redirect) {
        try {
            long groupId = inviteService.accept(token, user.id(), memberId, name);
            Flash.success(redirect, "You joined the group. Welcome!");
            return "redirect:/groups/" + groupId;
        } catch (BusinessRuleException e) {
            Flash.error(redirect, e.getMessage());
            return "redirect:/invites/" + token;
        }
    }
}
