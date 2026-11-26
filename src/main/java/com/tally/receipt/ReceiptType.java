package com.tally.receipt;

import java.util.Arrays;
import java.util.Optional;

/**
 * The file types a receipt may be, recognised by their first bytes ("magic numbers") rather than
 * by the file name or the browser's claim, so a renamed script can't be uploaded as a "photo".
 */
public enum ReceiptType {
    JPEG("image/jpeg", true),
    PNG("image/png", true),
    WEBP("image/webp", true),
    PDF("application/pdf", false);

    private final String contentType;
    private final boolean image;

    ReceiptType(String contentType, boolean image) {
        this.contentType = contentType;
        this.image = image;
    }

    public String getContentType() {
        return contentType;
    }

    public boolean isImage() {
        return image;
    }

    public static Optional<ReceiptType> detect(byte[] data) {
        if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(data, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        if (startsWith(data, 0, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    public static Optional<ReceiptType> fromContentType(String contentType) {
        return Arrays.stream(values()).filter(t -> t.contentType.equals(contentType)).findFirst();
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data == null || data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
