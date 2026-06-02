package com.vulkantechtt.konvo.realtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalRealtimeBusTest {

    @Mock SseHub hub;

    @Test
    void publishDelegatesToHubDeliverLocal() {
        LocalRealtimeBus bus = new LocalRealtimeBus(hub);
        UUID tenant = UUID.randomUUID();
        Map<String, String> payload = Map.of("a", "b");

        bus.publish(tenant, "message_appended", payload);

        verify(hub).deliverLocal(eq(tenant), eq("message_appended"), eq(payload));
    }
}
