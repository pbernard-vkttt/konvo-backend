package com.vulkantechtt.konvo.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * Naive character-based chunker. Splits on paragraph boundaries first, then
 * packs paragraphs into ~targetSize chunks with overlap. Good enough for the
 * text/FAQ content M5 supports. A token-aware splitter lands when we
 * introduce PDF + long-form sources.
 */
public final class Chunker {

    public static final int TARGET_SIZE = 1000;
    public static final int OVERLAP = 200;

    private Chunker() {}

    public static List<String> chunk(String input) {
        return chunk(input, TARGET_SIZE, OVERLAP);
    }

    public static List<String> chunk(String input, int targetSize, int overlap) {
        if (input == null) return List.of();
        String normalised = input.replaceAll("\\r\\n?", "\n").trim();
        if (normalised.isEmpty()) return List.of();

        // Step 1: split on blank lines (paragraphs) — preserves natural breaks.
        String[] paragraphs = normalised.split("\\n{2,}");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;
            if (current.length() == 0) {
                current.append(trimmed);
            } else if (current.length() + 2 + trimmed.length() <= targetSize) {
                current.append("\n\n").append(trimmed);
            } else {
                chunks.add(current.toString());
                current.setLength(0);
                current.append(trimmed);
            }
        }
        if (current.length() > 0) chunks.add(current.toString());

        // Step 2: any chunk that's still way over targetSize gets a hard split
        // with overlap. (Catches a single mega-paragraph.)
        List<String> out = new ArrayList<>(chunks.size());
        for (String c : chunks) {
            if (c.length() <= targetSize) {
                out.add(c);
            } else {
                hardSplit(c, targetSize, overlap, out);
            }
        }
        return out;
    }

    private static void hardSplit(String text, int targetSize, int overlap, List<String> out) {
        int step = Math.max(1, targetSize - overlap);
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + targetSize);
            out.add(text.substring(i, end).trim());
            if (end == text.length()) break;
            i += step;
        }
    }
}
