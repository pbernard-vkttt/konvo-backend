package com.vulkantechtt.konvo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.auth.dto.ForgotPasswordRequest;
import com.vulkantechtt.konvo.common.KonvoException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock AuthService authService;
    @Mock AuthCookieWriter cookies;
    @Mock RateLimitGuard rateLimit;
    @Mock HttpServletRequest http;

    @Test
    void forgotPasswordDoesNotReturnRawTokenWhenDevTokenDisabled() {
        when(authService.beginPasswordReset("a@b.tt")).thenReturn("raw-token");
        AuthController controller = new AuthController(authService, cookies, rateLimit, false);

        var response = controller.forgot(new ForgotPasswordRequest("a@b.tt"), http);

        assertThat(response.getBody()).isEqualTo(new AuthController.ForgotPasswordResponse(true));
    }

    @Test
    void forgotPasswordCanReturnRawTokenWhenDevTokenEnabled() {
        when(authService.beginPasswordReset("a@b.tt")).thenReturn("raw-token");
        AuthController controller = new AuthController(authService, cookies, rateLimit, true);

        var response = controller.forgot(new ForgotPasswordRequest("a@b.tt"), http);

        assertThat(response.getBody()).isEqualTo(new AuthController.DevForgotPasswordResponse(true, "raw-token"));
    }

    @Test
    void forgotPasswordRateLimitShortCircuitsBeforeHittingTheService() {
        doThrow(KonvoException.tooManyRequests("slow down", 30))
                .when(rateLimit).checkForgotPassword(any(), eq("a@b.tt"));
        AuthController controller = new AuthController(authService, cookies, rateLimit, false);

        assertThatThrownBy(() -> controller.forgot(new ForgotPasswordRequest("a@b.tt"), http))
                .isInstanceOf(KonvoException.class);

        // The reset flow must not run once the limiter trips.
        verifyNoInteractions(authService);
    }
}
