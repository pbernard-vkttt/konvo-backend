package com.vulkantechtt.konvo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.auth.dto.AuthSessionResponse;
import com.vulkantechtt.konvo.auth.dto.ForgotPasswordRequest;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
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

    @Test
    void meUsesPrincipalWhenAlreadyAuthenticated() {
        AuthController controller = new AuthController(authService, cookies, rateLimit, false);
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        KonvoPrincipal principal = new KonvoPrincipal(
                userId,
                "owner@example.com",
                "Owner",
                tenantId,
                Role.OWNER);
        AuthSessionResponse responseBody = sessionResponse();
        when(authService.sessionFromPrincipal(principal)).thenReturn(responseBody);

        var response = controller.me(principal, http);

        assertThat(response.getBody()).isEqualTo(responseBody);
        verify(authService).sessionFromPrincipal(principal);
    }

    @Test
    void meFallsBackToRefreshCookieWhenPrincipalMissing() {
        AuthController controller = new AuthController(authService, cookies, rateLimit, false);
        AuthSessionResponse responseBody = sessionResponse();
        AuthService.Session session = new AuthService.Session("access", 900, "refresh", responseBody);
        doReturn("refresh").when(cookies).readFromRequest(http);
        when(authService.refresh("refresh", http)).thenReturn(session);
        when(cookies.build("refresh")).thenReturn(org.springframework.http.ResponseCookie.from("konvo_refresh", "refresh").build());

        var response = controller.me(null, http);

        assertThat(response.getBody()).isEqualTo(responseBody);
        assertThat(response.getHeaders().getFirst(org.springframework.http.HttpHeaders.SET_COOKIE))
                .contains("konvo_refresh=refresh");
    }

    private static AuthSessionResponse sessionResponse() {
        return new AuthSessionResponse(
                "access",
                900,
                new AuthSessionResponse.UserSummary(UUID.randomUUID(), "owner@example.com", "Owner"),
                new AuthSessionResponse.TenantSummary(UUID.randomUUID(), "Workspace", "workspace", Role.OWNER, true));
    }
}
