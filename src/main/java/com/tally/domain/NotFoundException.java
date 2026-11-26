package com.tally.domain;

/** Thrown when a group, member or expense does not exist. Rendered as a 404 page. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, long id) {
        super(what + " " + id + " was not found");
    }
}
