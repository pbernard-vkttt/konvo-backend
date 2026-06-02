package com.vulkantechtt.konvo.common;

/**
 * Small text sanitizer for values that cross trust boundaries into prompts,
 * email subjects, and transactional email bodies.
 */
public final class SafeText {

    private SafeText() {}

    public static String singleLine(String value, String fallback, int maxLength) {
        String cleaned = value == null ? "" : value
                .replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) {
            cleaned = fallback;
        }
        if (cleaned == null) {
            cleaned = "";
        }
        if (maxLength > 0 && cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength);
        }
        return cleaned;
    }
}
