package com.tally.service;

/** Input for creating or editing a group. */
public record GroupCommand(String name, String description, String currency) {
}
