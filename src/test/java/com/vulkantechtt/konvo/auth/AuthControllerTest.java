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
import com.vulkantechtt.konvo.auth.dto.ResetPasswordRequest;
import com.vulkantechtt.konvo.auth.dto.RegisterOwnerRequest;
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
    void resendVerificationEmailRateLimitShortCircuitsBeforeHittingTheService() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        KonvoPrincipal principal = new KonvoPrincipal(
                userId,
                "owner@example.com",
                "Owner",
                tenantId,
                Role.OWNER);
        doThrow(KonvoException.tooManyRequests("slow down", 30))
                .when(rateLimit).checkEmailVerificationResend(any(), eq("owner@example.com"));
        AuthController controller = new AuthController(authService, cookies, rateLimit, false);

        assertThatThrownBy(() -> controller.resendVerificationEmail(principal, http))
                .isInstanceOf(KonvoException.class);

        verifyNoInteractions(authService);
    }

    @Test
    void resendVerificationEmailDelegatesForAuthenticatedPrincipal() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        KonvoPrincipal principal = new KonvoPrincipal(
                userId,
                "owner@example.com",
                "Owner",
                tenantId,
                Role.OWNER);
        AuthController controller = new AuthController(authService, cookies, rateLimit, false);

        var response = controller.resendVerificationEmail(principal, http);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);
        verify(rateLimit).checkEmailVerificationResend(any(), eq("owner@example.com"));
        verify(authService).resendVerificationEmail(principal);
    }

    @Test
    void resetPasswordClearsRefreshCookie() {
        AuthController controller = new AuthController(authService, cookies, rateLimit, false);
        when(cookies.buildCleared()).thenReturn(
                org.springframework.http.ResponseCookie.from("konvo_refresh", "")
                        .path("/api/v1/auth")
                        .maxAge(0)
                        .httpOnly(true)
                        .build());

        var response = controller.reset(new ResetPasswordRequest("raw-token", "new-password-123"));

        verify(authService).completePasswordReset(new ResetPasswordRequest("raw-token", "new-password-123"));
        assertThat(response.getHeaders().getFirst(org.springframework.http.HttpHeaders.SET_COOKIE))
                .contains("konvo_refresh=", "Max-Age=0");
    }

    @Test
    void registerOwnerReplacesPresentedRefreshCookie() {
        AuthController controller = new AuthController(authService, cookies, rateLimit, false);
        RegisterOwnerRequest request = new RegisterOwnerRequest(
                "Owner Name",
                "owner@example.com",
                "super-secret-password");
        AuthSessionResponse responseBody = sessionResponse();
        AuthService.Session session = new AuthService.Session("access", 900, "new-refresh", responseBody);
        doReturn("old-refresh").when(cookies).readFromRequest(http);
        when(authService.registerOwner(request, "old-refresh", http)).thenReturn(session);
        when(cookies.build("new-refresh"))
                .thenReturn(org.springframework.http.ResponseCookie.from("konvo_refresh", "new-refresh").build());

        var response = controller.registerOwner(request, http);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(responseBody);
        assertThat(response.getHeaders().getFirst(org.springframework.http.HttpHeaders.SET_COOKIE))
                .contains("konvo_refresh=new-refresh");
        verify(authService).registerOwner(request, "old-refresh", http);
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
                new AuthSessionResponse.UserSummary(UUID.randomUUID(), "owner@example.com", "Owner", true),
                new AuthSessionResponse.TenantSummary(UUID.randomUUID(), "Workspace", "workspace", Role.OWNER, true));
    }
}
