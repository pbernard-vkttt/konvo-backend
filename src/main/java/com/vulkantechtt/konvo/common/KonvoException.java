package com.vulkantechtt.konvo.common;

import org.springframework.http.HttpStatus;

public class KonvoException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Integer retryAfterSeconds;

    public KonvoException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public KonvoException(HttpStatus status, String code, String message, Integer retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    /** Seconds the client should wait before retrying, surfaced as Retry-After. */
    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }

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

    public static KonvoException tooManyRequests(String reason, int retryAfterSeconds) {
        return new KonvoException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", reason, retryAfterSeconds);
    }
}
