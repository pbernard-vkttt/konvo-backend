package com.vulkantechtt.konvo.realtime;

import java.util.UUID;

/**
 * Tenant-scoped pub/sub abstraction behind {@link SseHub#broadcast}. The
 * default {@link LocalRealtimeBus} writes directly to the in-process emitter
 * set; the {@link RedisRealtimeBus} hands off to Redis pub/sub so every
 * pod's SSE hub receives the payload (multi-instance deploys, M8).
 *
 * Implementations must guarantee {@code dispatch} is called on every pod
 * exactly once per publish — including the publisher itself, since SSE
 * emitters might live anywhere.
 */
public interface RealtimeBus {

    void publish(UUID tenantId, String event, Object payload);

    /** Identifier for logs ({@code "local"} or {@code "redis"}). */
    String name();
}
