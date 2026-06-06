package com.vulkantechtt.konvo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.Role;
import com.vulkantechtt.konvo.users.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EmailVerificationGuardTest {

    @Mock UserRepository users;

    @Test
    void allowsVerifiedUsers() {
        UUID userId = UUID.randomUUID();
        when(users.existsByIdAndEmailVerifiedTrue(userId)).thenReturn(true);
        EmailVerificationGuard guard = new EmailVerificationGuard(users);

        assertThatCode(() -> guard.requireVerified(principal(userId)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnverifiedUsersWithSpecificCode() {
        UUID userId = UUID.randomUUID();
        when(users.existsByIdAndEmailVerifiedTrue(userId)).thenReturn(false);
        EmailVerificationGuard guard = new EmailVerificationGuard(users);

        assertThatThrownBy(() -> guard.requireVerified(principal(userId)))
                .isInstanceOfSatisfying(KonvoException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo("email_verification_required");
                });
    }

    private static KonvoPrincipal principal(UUID userId) {
        return new KonvoPrincipal(
                userId,
                "owner@example.com",
                "Owner",
                UUID.randomUUID(),
                Role.OWNER);
    }
}
