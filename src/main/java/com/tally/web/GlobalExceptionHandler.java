package com.tally.web;

import com.tally.domain.NotFoundException;
import com.tally.invite.InvalidInviteException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Turns domain exceptions into friendly pages instead of stack traces. */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NotFoundException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "error/404";
    }

    /** A receipt over the upload limit. Spring rejects it before any controller runs. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public String tooLarge() {
        return "error/too-large";
    }

    /** Unknown, expired and switched-off links all look the same, so nobody learns which links existed. */
    @ExceptionHandler(InvalidInviteException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String invalidInvite() {
        return "invites/invalid";
    }
}
