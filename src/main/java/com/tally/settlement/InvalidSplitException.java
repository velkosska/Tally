package com.tally.settlement;

/** The numbers given for an uneven split don't work, for example percentages that don't add up to 100. */
public class InvalidSplitException extends IllegalArgumentException {

    public InvalidSplitException(String message) {
        super(message);
    }
}
