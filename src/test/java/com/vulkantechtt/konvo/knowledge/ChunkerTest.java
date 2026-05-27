package com.vulkantechtt.konvo.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    @Test
    void emptyInputReturnsNoChunks() {
        assertThat(Chunker.chunk(null)).isEmpty();
        assertThat(Chunker.chunk("")).isEmpty();
        assertThat(Chunker.chunk("   \n\n   ")).isEmpty();
    }

    @Test
    void shortContentIsOneChunk() {
        List<String> chunks = Chunker.chunk("We open from 6 am to 10 pm.");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("6 am to 10 pm");
    }

    @Test
    void paragraphsArePackedIntoTargetSize() {
        // Two paragraphs that together fit under the default target.
        List<String> chunks = Chunker.chunk("First paragraph.\n\nSecond paragraph.");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("First paragraph.");
        assertThat(chunks.get(0)).contains("Second paragraph.");
    }

    @Test
    void hardSplitsRunawayParagraph() {
        // Single paragraph way above target size — must produce multiple chunks.
        String huge = "a".repeat(3500);
        List<String> chunks = Chunker.chunk(huge);
        assertThat(chunks).hasSizeGreaterThan(2);
        for (String c : chunks) {
            assertThat(c.length()).isLessThanOrEqualTo(Chunker.TARGET_SIZE);
        }
    }

    @Test
    void normalizesCrlf() {
        List<String> chunks = Chunker.chunk("line1\r\n\r\nline2");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("line1");
        assertThat(chunks.get(0)).contains("line2");
    }
}
