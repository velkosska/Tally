package com.tally.receipt;

/** A receipt's bytes and how to send them to the browser. */
public record ReceiptFile(String fileName, ReceiptType type, byte[] data) {
}
