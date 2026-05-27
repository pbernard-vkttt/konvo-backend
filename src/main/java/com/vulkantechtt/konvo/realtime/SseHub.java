package com.vulkantechtt.konvo.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Tenant-scoped fan-out of in-process events to SSE subscribers. Listeners
 * (ingest, outbound, etc.) call {@link #broadcast} once their persistence
 * commits; this hub pushes the payload to every emitter registered for that
 * tenant.
 *
 * Single-instance only for M4 — when we go multi-pod (M8), this gets replaced
 * with a Redis pub/sub fan-out so a webhook landing on pod A can reach an
 * agent's open EventSource on pod B.
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    private final Map<UUID, CopyOnWriteArraySet<SseEmitter>> tenants = new ConcurrentHashMap<>();

    public SseEmitter register(UUID tenantId) {
        SseEmitter emitter = new SseEmitter(0L); // never time out from server side
        tenants.computeIfAbsent(tenantId, t -> new CopyOnWriteArraySet<>()).add(emitter);
        Runnable cleanup = () -> {
            var set = tenants.get(tenantId);
            if (set != null) set.remove(emitter);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("ready").data("ok"));
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    public void broadcast(UUID tenantId, String eventName, Object payload) {
        var set = tenants.get(tenantId);
        if (set == null || set.isEmpty()) return;
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                log.debug("Dropping dead SSE emitter for tenant={}: {}", tenantId, e.toString());
                set.remove(emitter);
            }
        }
    }

    int emitterCount(UUID tenantId) {
        var set = tenants.get(tenantId);
        return set == null ? 0 : set.size();
    }
}
