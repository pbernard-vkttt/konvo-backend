package com.vulkantechtt.konvo.scheduling.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulkantechtt.konvo.common.KonvoException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OAuthStateCodecTest {

    private final OAuthStateCodec codec = new OAuthStateCodec("test-signing-secret-at-least-32-chars-long!!");

    @Test
    void roundTripsTenantAndUser() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String state = codec.issue(tenant, user);

        OAuthStateCodec.State decoded = codec.verify(state);

        assertThat(decoded.tenantId()).isEqualTo(tenant);
        assertThat(decoded.userId()).isEqualTo(user);
    }

    @Test
    void rejectsTamperedSignature() {
        String state = codec.issue(UUID.randomUUID(), UUID.randomUUID());
        String tampered = state.substring(0, state.lastIndexOf('.')) + ".AAAA";
        assertThatThrownBy(() -> codec.verify(tampered)).isInstanceOf(KonvoException.class);
    }

    @Test
    void rejectsForeignSecret() {
        String state = codec.issue(UUID.randomUUID(), UUID.randomUUID());
        OAuthStateCodec other = new OAuthStateCodec("a-totally-different-secret-key-32-chars-xx");
        assertThatThrownBy(() -> other.verify(state)).isInstanceOf(KonvoException.class);
    }

    @Test
    void rejectsMalformedState() {
        assertThatThrownBy(() -> codec.verify("not-a-valid-state")).isInstanceOf(KonvoException.class);
        assertThatThrownBy(() -> codec.verify(null)).isInstanceOf(KonvoException.class);
    }
}
