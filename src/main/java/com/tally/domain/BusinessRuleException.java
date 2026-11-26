package com.tally.domain;

/** Thrown when a request is well-formed but breaks a rule of the domain, e.g. a duplicate member name. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
