package com.vulkantechtt.konvo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulkantechtt.konvo.users.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final AuthProperties props = props();
    private final JwtService service = new JwtService(props);

    @Test
    void roundTripsClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String token = service.issueAccessToken(userId, "a@b.tt", "Aaliyah", tenantId, Role.OWNER);

        JwtService.ParsedToken parsed = service.parse(token);

        assertThat(parsed.userId()).isEqualTo(userId);
        assertThat(parsed.tenantId()).isEqualTo(tenantId);
        assertThat(parsed.email()).isEqualTo("a@b.tt");
        assertThat(parsed.fullName()).isEqualTo("Aaliyah");
        assertThat(parsed.role()).isEqualTo(Role.OWNER);
        assertThat(parsed.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void rejectsTokensSignedWithDifferentSecret() {
        AuthProperties other = props();
        other.setSigningSecret("a-completely-different-32-character-secret-for-test");
        JwtService imposter = new JwtService(other);
        String token = imposter.issueAccessToken(UUID.randomUUID(), "x@y.tt", "X", UUID.randomUUID(), Role.AGENT);

        assertThatThrownBy(() -> service.parse(token))
                .isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> service.parse("not.a.jwt"))
                .isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void requiresStrongSecret() {
        AuthProperties bad = new AuthProperties();
        bad.setSigningSecret("too-short");
        assertThatThrownBy(() -> new JwtService(bad))
                .isInstanceOf(IllegalStateException.class);
    }

    private static AuthProperties props() {
        AuthProperties p = new AuthProperties();
        p.setSigningSecret("test-only-signing-secret-32-characters-minimum");
        p.setAccessTokenTtlMinutes(15);
        p.setRefreshTokenTtlDays(30);
        return p;
    }
}
