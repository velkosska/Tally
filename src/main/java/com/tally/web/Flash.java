package com.tally.web;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** One place for the flash messages shown as toasts after a redirect. */
final class Flash {

    private Flash() {
    }

    static void success(RedirectAttributes attributes, String message) {
        attributes.addFlashAttribute("success", message);
    }

    static void error(RedirectAttributes attributes, String message) {
        attributes.addFlashAttribute("error", message);
    }
}
