package com.tally.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExporterTest {

    @Test
    void plainTextIsLeftAlone() {
        assertThat(CsvExporter.escape("Pizza")).isEqualTo("Pizza");
    }

    @Test
    void commasAndQuotesAreQuoted() {
        assertThat(CsvExporter.escape("Fish, chips")).isEqualTo("\"Fish, chips\"");
        assertThat(CsvExporter.escape("The \"best\" pizza")).isEqualTo("\"The \"\"best\"\" pizza\"");
    }

    @Test
    void formulasAreDefused() {
        assertThat(CsvExporter.escape("=HYPERLINK(\"x\")")).startsWith("\"'=");
        assertThat(CsvExporter.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }

    @Test
    void negativeNumbersStayNumbers() {
        assertThat(CsvExporter.escape("-12.50")).isEqualTo("-12.50");
    }
}
