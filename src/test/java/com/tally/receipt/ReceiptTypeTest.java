package com.tally.receipt;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Recognising receipt files by their first bytes, and cleaning up their names. */
class ReceiptTypeTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P', 'V', 'P', '8'};
    static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    void recognisesEachAllowedType() {
        assertThat(ReceiptType.detect(JPEG)).contains(ReceiptType.JPEG);
        assertThat(ReceiptType.detect(PNG)).contains(ReceiptType.PNG);
        assertThat(ReceiptType.detect(WEBP)).contains(ReceiptType.WEBP);
        assertThat(ReceiptType.detect(PDF)).contains(ReceiptType.PDF);
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(ReceiptType.detect("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ReceiptType.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
        assertThat(ReceiptType.detect(new byte[]{'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'A', 'V', 'E'})).isEmpty();
        assertThat(ReceiptType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(ReceiptType.detect(new byte[0])).isEmpty();
        assertThat(ReceiptType.detect(null)).isEmpty();
    }

    @Test
    void onlyImagesAreShownInline() {
        assertThat(ReceiptType.JPEG.isImage()).isTrue();
        assertThat(ReceiptType.PDF.isImage()).isFalse();
        assertThat(ReceiptType.fromContentType("image/png")).contains(ReceiptType.PNG);
        assertThat(ReceiptType.fromContentType("text/html")).isEmpty();
    }

    @Test
    void namesAreCleanedAndGetTheRightExtension() {
        assertThat(ReceiptService.cleanName("dinner.jpg", ReceiptType.JPEG)).isEqualTo("dinner.jpg");
        assertThat(ReceiptService.cleanName("IMG_2041.JPEG", ReceiptType.JPEG)).isEqualTo("IMG_2041.JPEG");
        assertThat(ReceiptService.cleanName("scan", ReceiptType.PDF)).isEqualTo("scan.pdf");
        assertThat(ReceiptService.cleanName("photo.png", ReceiptType.JPEG)).isEqualTo("photo.png.jpg");
        assertThat(ReceiptService.cleanName("bill\"; evil=\r\n.pdf", ReceiptType.PDF)).isEqualTo("bill evil.pdf");
        assertThat(ReceiptService.cleanName("../../etc/passwd", ReceiptType.PNG)).isEqualTo("receipt.png");
        assertThat(ReceiptService.cleanName("Račun €.webp", ReceiptType.WEBP)).isEqualTo("Raun .webp");
        assertThat(ReceiptService.cleanName(null, ReceiptType.PNG)).isEqualTo("receipt.png");
        assertThat(ReceiptService.cleanName("x".repeat(300), ReceiptType.PNG)).hasSize(154);
    }
}
