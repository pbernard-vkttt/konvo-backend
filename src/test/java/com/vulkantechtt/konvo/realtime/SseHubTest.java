package com.vulkantechtt.konvo.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SseHubTest {

    @Mock RealtimeBus bus;
    @Mock SseEmitter deadEmitter;
    @Mock SseEmitter liveEmitter;

    @Test
    void deliveryRemovesCompletedEmitterAndContinuesFanOut() throws IOException {
        SseHub hub = new SseHub(bus);
        UUID tenantId = UUID.randomUUID();
        hub.register(tenantId, deadEmitter);
        hub.register(tenantId, liveEmitter);
        clearInvocations(deadEmitter, liveEmitter);
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(deadEmitter).send(any(SseEmitter.SseEventBuilder.class));

        hub.deliverLocal(tenantId, "message_appended", "hello");

        assertThat(hub.emitterCount(tenantId)).isEqualTo(1);
        verify(liveEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void readySendFailureDuringRegistrationRemovesEmitterImmediately() throws IOException {
        SseHub hub = new SseHub(bus);
        UUID tenantId = UUID.randomUUID();
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(deadEmitter).send(any(SseEmitter.SseEventBuilder.class));

        hub.register(tenantId, deadEmitter);

        assertThat(hub.emitterCount(tenantId)).isZero();
    }
}
