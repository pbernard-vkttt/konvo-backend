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
 * Tenant-scoped fan-out of events to SSE subscribers. Domain code calls
 * {@link #broadcast}; the configured {@link RealtimeBus} decides whether
 * that delivery stays local (single-pod default) or routes through Redis
 * pub/sub (multi-pod, M8). The bus eventually calls back into
 * {@link #deliverLocal} on every pod, which is what actually writes to the
 * SSE emitters that pod is hosting.
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    private final Map<UUID, CopyOnWriteArraySet<SseEmitter>> tenants = new ConcurrentHashMap<>();
    private final RealtimeBus bus;

    public SseHub(RealtimeBus bus) {
        this.bus = bus;
    }

    public SseEmitter register(UUID tenantId) {
        SseEmitter emitter = new SseEmitter(0L); // never time out from server side
        return register(tenantId, emitter);
    }

    SseEmitter register(UUID tenantId, SseEmitter emitter) {
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
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping dead SSE emitter during registration for tenant={}: {}", tenantId, e.toString());
            cleanup.run();
        }
        return emitter;
    }

    /**
     * Public broadcast: hands off to the configured {@link RealtimeBus}.
     * Domain code should call this — never {@link #deliverLocal}.
     */
    public void broadcast(UUID tenantId, String eventName, Object payload) {
        bus.publish(tenantId, eventName, payload);
    }

    /**
     * Writes to this pod's emitters only. Called by the bus once the message
     * has been routed (immediately for local, via Redis subscriber for the
     * Redis impl). Package-private so domain code can't bypass the bus.
     */
    void deliverLocal(UUID tenantId, String eventName, Object payload) {
        var set = tenants.get(tenantId);
        if (set == null || set.isEmpty()) return;
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException e) {
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
