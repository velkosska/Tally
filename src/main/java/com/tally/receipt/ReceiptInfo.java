package com.tally.receipt;

import java.time.Instant;

/** What the expense page shows about a receipt, without loading the file itself. */
public record ReceiptInfo(String fileName, String contentType, int sizeBytes, Instant uploadedAt) {

    public boolean isImage() {
        return ReceiptType.fromContentType(contentType).map(ReceiptType::isImage).orElse(false);
    }

    /** "245 KB" or "1.2 MB". */
    public String size() {
        if (sizeBytes < 1024 * 1024) {
            return Math.max(1, Math.round(sizeBytes / 1024.0)) + " KB";
        }
        return String.format(java.util.Locale.UK, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }
}
