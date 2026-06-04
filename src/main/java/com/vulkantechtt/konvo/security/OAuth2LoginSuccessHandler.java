package com.vulkantechtt.konvo.security;

import com.vulkantechtt.konvo.auth.AuthCookieWriter;
import com.vulkantechtt.konvo.auth.AuthService;
import com.vulkantechtt.konvo.auth.GoogleProfile;
import com.vulkantechtt.konvo.common.SafeText;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final AuthService authService;
    private final AuthCookieWriter cookies;
    private final String appBaseUrl;

    public OAuth2LoginSuccessHandler(
            AuthService authService,
            AuthCookieWriter cookies,
            @Value("${konvo.public-base-url.app}") String appBaseUrl) {
        this.authService = authService;
        this.cookies = cookies;
        String base = SafeText.singleLine(appBaseUrl, "http://localhost:4200", 300);
        this.appBaseUrl = base.replaceAll("/$", "");
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        try {
            OAuth2User user = (OAuth2User) authentication.getPrincipal();
            GoogleProfile profile = googleProfile(user.getAttributes());
            AuthService.Session session = authService.loginWithGoogle(profile, request);
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.build(session.refreshTokenRaw()).toString());
            var httpSession = request.getSession(false);
            if (httpSession != null) {
                httpSession.invalidate();
            }
            response.sendRedirect(appBaseUrl + "/auth/success");
        } catch (RuntimeException ex) {
            log.warn("Google login handoff failed", ex);
            response.sendRedirect(appBaseUrl + "/login?reason=google_auth_failed");
        }
    }

    private static GoogleProfile googleProfile(Map<String, Object> attributes) {
        String subject = SafeText.singleLine((String) attributes.get("sub"), "", 255);
        String email = SafeText.singleLine((String) attributes.get("email"), "", 254).toLowerCase();
        String name = SafeText.singleLine((String) attributes.get("name"), "", 160);
        String picture = SafeText.singleLine((String) attributes.get("picture"), "", 500);
        return new GoogleProfile(subject, email, name, picture);
    }
}
