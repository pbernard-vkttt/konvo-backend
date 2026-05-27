package com.vulkantechtt.konvo.knowledge;

/**
 * Formats a {@code float[]} as the literal pgvector accepts —
 * {@code "[v1,v2,...,vN]"} — so we can pass embeddings through plain
 * {@code JdbcTemplate} parameters with a {@code ?::vector} cast.
 *
 * Chose this over adding pgvector-java + a Hibernate UserType: one helper is
 * smaller than a third-party dep, and the SELECT paths use native SQL anyway
 * because the cosine operator ({@code <=>}) isn't representable in JPQL.
 */
public final class VectorFormatter {

    private VectorFormatter() {}

    public static String toLiteral(float[] embedding) {
        if (embedding == null) {
            throw new IllegalArgumentException("embedding is required");
        }
        StringBuilder sb = new StringBuilder(embedding.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            // Postgres parses both finite literals and exponential notation; using
            // Float.toString keeps full precision while staying compact.
            sb.append(Float.toString(embedding[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
