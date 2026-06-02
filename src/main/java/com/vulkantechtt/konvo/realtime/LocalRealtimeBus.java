package com.vulkantechtt.konvo.realtime;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * In-process bus — dispatches straight to {@link SseHub#deliverLocal} on the
 * same pod. The default when {@code konvo.realtime.bus} is unset or set to
 * {@code local}.
 */
@Component
@ConditionalOnProperty(name = "konvo.realtime.bus", havingValue = "local", matchIfMissing = true)
@ConditionalOnMissingBean(RealtimeBus.class)
public class LocalRealtimeBus implements RealtimeBus {

    private final SseHub hub;

    public LocalRealtimeBus(@Lazy @Autowired SseHub hub) {
        this.hub = hub;
    }

    @Override
    public void publish(UUID tenantId, String event, Object payload) {
        hub.deliverLocal(tenantId, event, payload);
    }

    @Override
    public String name() { return "local"; }
}
