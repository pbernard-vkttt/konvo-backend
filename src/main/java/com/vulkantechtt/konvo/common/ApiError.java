package com.vulkantechtt.konvo.common;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        Instant timestamp,
        String path,
        List<FieldViolation> errors) {

    public static ApiError of(String code, String message, String path) {
        return new ApiError(code, message, Instant.now(), path, List.of());
    }

    public static ApiError of(String code, String message, String path, List<FieldViolation> errors) {
        return new ApiError(code, message, Instant.now(), path, errors);
    }

    public record FieldViolation(String field, String message) {}
}
