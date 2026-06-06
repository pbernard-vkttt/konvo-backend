package com.vulkantechtt.konvo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulkantechtt.konvo.common.KonvoException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RateLimitGuardTest {

    @Test
    void allowsRequestsUpToThePerEmailCapacityThenBlocks() {
        RateLimitGuard guard = new RateLimitGuard(true);
        // LOGIN_PER_EMAIL capacity is 8 — the 9th attempt for the same account
        // must be rejected (the per-IP limit of 15 hasn't tripped yet).
        for (int i = 0; i < 8; i++) {
            int attempt = i;
            assertThatCode(() -> guard.checkLogin("10.0.0.1", "victim@konvelo.io"))
                    .as("attempt %d", attempt)
                    .doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> guard.checkLogin("10.0.0.1", "victim@konvelo.io"))
                .isInstanceOf(KonvoException.class);
    }

    @Test
    void perIpLimitTripsAcrossDistinctEmails() {
        RateLimitGuard guard = new RateLimitGuard(true);
        // Distinct emails each time so the per-email limit never trips; the
        // per-IP limit (15) is what should fire on the 16th request.
        for (int i = 0; i < 15; i++) {
            guard.checkLogin("10.0.0.2", "user" + i + "@konvelo.io");
        }
        assertThatThrownBy(() -> guard.checkLogin("10.0.0.2", "user-last@konvelo.io"))
                .isInstanceOf(KonvoException.class);
    }

    @Test
    void rejectionCarries429AndRetryAfter() {
        RateLimitGuard guard = new RateLimitGuard(true);
        for (int i = 0; i < 4; i++) {
            guard.checkForgotPassword("10.0.0.3", "spam@konvelo.io");
        }
        assertThatThrownBy(() -> guard.checkForgotPassword("10.0.0.3", "spam@konvelo.io"))
                .isInstanceOfSatisfying(KonvoException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getRetryAfterSeconds()).isNotNull().isGreaterThanOrEqualTo(1);
                });
    }

    @Test
    void emailIsNormalisedSoCaseDoesNotBypassTheLimit() {
        RateLimitGuard guard = new RateLimitGuard(true);
        guard.checkForgotPassword("10.0.0.4", "Mixed@Konvelo.IO");
        guard.checkForgotPassword("10.0.0.4", "mixed@konvelo.io");
        guard.checkForgotPassword("10.0.0.4", "MIXED@KONVELO.IO");
        guard.checkForgotPassword("10.0.0.4", "mixed@konvelo.io");
        // 4 forgot requests for the same (normalised) email exhausts the limit.
        assertThatThrownBy(() -> guard.checkForgotPassword("10.0.0.4", "mixed@konvelo.io"))
                .isInstanceOf(KonvoException.class);
    }

    @Test
    void verificationResendIsLimitedPerEmail() {
        RateLimitGuard guard = new RateLimitGuard(true);
        guard.checkEmailVerificationResend("10.0.0.6", "Owner@Konvelo.IO");
        guard.checkEmailVerificationResend("10.0.0.6", "owner@konvelo.io");
        guard.checkEmailVerificationResend("10.0.0.6", "OWNER@KONVELO.IO");

        assertThatThrownBy(() -> guard.checkEmailVerificationResend("10.0.0.6", "owner@konvelo.io"))
                .isInstanceOfSatisfying(KonvoException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void disabledGuardNeverThrows() {
        RateLimitGuard guard = new RateLimitGuard(false);
        assertThatCode(() -> {
            for (int i = 0; i < 1000; i++) {
                guard.checkLogin("10.0.0.5", "flood@konvelo.io");
                guard.checkForgotPassword("10.0.0.5", "flood@konvelo.io");
                guard.checkEmailVerificationResend("10.0.0.5", "flood@konvelo.io");
            }
        }).doesNotThrowAnyException();
    }
}
