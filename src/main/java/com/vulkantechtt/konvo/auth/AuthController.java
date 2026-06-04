package com.vulkantechtt.konvo.auth;

import com.vulkantechtt.konvo.auth.dto.AcceptInvitationRequest;
import com.vulkantechtt.konvo.auth.dto.VerifyEmailRequest;
import com.vulkantechtt.konvo.auth.dto.AuthSessionResponse;
import com.vulkantechtt.konvo.auth.dto.ForgotPasswordRequest;
import com.vulkantechtt.konvo.auth.dto.InvitationPreviewResponse;
import com.vulkantechtt.konvo.auth.dto.LoginRequest;
import com.vulkantechtt.konvo.auth.dto.RegisterOwnerRequest;
import com.vulkantechtt.konvo.auth.dto.ResetPasswordRequest;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieWriter cookies;
    private final RateLimitGuard rateLimit;
    private final boolean devTokenInResponse;

    public AuthController(
            AuthService authService,
            AuthCookieWriter cookies,
            RateLimitGuard rateLimit,
            @Value("${konvo.auth.dev-token-in-response:false}") boolean devTokenInResponse) {
        this.authService = authService;
        this.cookies = cookies;
        this.rateLimit = rateLimit;
        this.devTokenInResponse = devTokenInResponse;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest http) {
        rateLimit.checkLogin(clientIp(http), req.email());
        AuthService.Session session = authService.login(req, http);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.build(session.refreshTokenRaw()).toString())
                .body(session.body());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(HttpServletRequest http) {
        AuthService.Session session = authService.refresh(cookies.readFromRequest(http), http);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.build(session.refreshTokenRaw()).toString())
                .body(session.body());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        authService.logout(cookies.readFromRequest(http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.buildCleared().toString())
                .build();
    }

    @PostMapping("/register-owner")
    public ResponseEntity<AuthSessionResponse> registerOwner(
            @Valid @RequestBody RegisterOwnerRequest req,
            HttpServletRequest http) {
        AuthService.Session session = authService.registerOwner(req, http);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookies.build(session.refreshTokenRaw()).toString())
                .body(session.body());
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<?> forgot(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        rateLimit.checkForgotPassword(clientIp(http), req.email());
        String raw = authService.beginPasswordReset(req.email());
        if (devTokenInResponse) {
            return ResponseEntity.ok(new DevForgotPasswordResponse(true, raw));
        }
        return ResponseEntity.ok(new ForgotPasswordResponse(true));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest req) {
        authService.completePasswordReset(req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        authService.verifyEmail(req.token());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/invitations/{token}/preview")
    public InvitationPreviewResponse previewInvitation(@PathVariable String token) {
        return authService.previewInvitation(token);
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<AuthSessionResponse> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest req,
            HttpServletRequest http) {
        AuthService.Session session = authService.acceptInvitation(req, http);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.build(session.refreshTokenRaw()).toString())
                .body(session.body());
    }

    @GetMapping("/me")
    public ResponseEntity<AuthSessionResponse> me(
            @AuthenticationPrincipal KonvoPrincipal principal,
            HttpServletRequest http) {
        if (principal != null) {
            return ResponseEntity.ok(authService.sessionFromPrincipal(principal));
        }
        AuthService.Session session = authService.refresh(cookies.readFromRequest(http), http);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.build(session.refreshTokenRaw()).toString())
                .body(session.body());
    }

    /**
     * Best-effort client IP for rate limiting. Behind Traefik the real client
     * is in {@code X-Forwarded-For} (first hop); fall back to the socket address
     * for direct/local calls.
     */
    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return http.getRemoteAddr();
    }

    /**
     * The raw token surface is dev-only: forgotten-password emails (M5/M6) will
     * never echo the token in the response. We still respond {@code ok:true}
     * unconditionally so the endpoint can't be used to probe email existence.
     */
    public record ForgotPasswordResponse(boolean ok) {}

    public record DevForgotPasswordResponse(boolean ok, String devToken) {}
}
