package com.vulkantechtt.konvo.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulkantechtt.konvo.common.KonvoException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TextExtractionServiceTest {

    private final TextExtractionService extractor = new TextExtractionService();

    @Test
    void extractsTextFromCsvUpload() {
        byte[] csv = "greeting,value\nhello,world\n".getBytes(StandardCharsets.UTF_8);

        String text = extractor.extractFromFile(csv, "menu.csv", "text/csv");

        assertThat(text).contains("hello").contains("world");
    }

    @Test
    void rejectsEmptyUpload() {
        assertThatThrownBy(() -> extractor.extractFromFile(new byte[0], "empty.csv", "text/csv"))
                .isInstanceOf(KonvoException.class);
    }

    @Test
    void rejectsNonHttpUrl() {
        assertThatThrownBy(() -> extractor.extractFromUrl("ftp://example.com/file"))
                .isInstanceOf(KonvoException.class);
    }
}
