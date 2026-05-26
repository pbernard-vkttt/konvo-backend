package com.vulkantechtt.konvo.common;

import org.springframework.http.HttpStatus;

public class KonvoException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public KonvoException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static KonvoException notFound(String entity, Object id) {
        return new KonvoException(HttpStatus.NOT_FOUND, "not_found",
                entity + " not found: " + id);
    }

    public static KonvoException forbidden(String reason) {
        return new KonvoException(HttpStatus.FORBIDDEN, "forbidden", reason);
    }

    public static KonvoException conflict(String reason) {
        return new KonvoException(HttpStatus.CONFLICT, "conflict", reason);
    }

    public static KonvoException badRequest(String reason) {
        return new KonvoException(HttpStatus.BAD_REQUEST, "bad_request", reason);
    }
}
