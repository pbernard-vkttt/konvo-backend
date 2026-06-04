package com.vulkantechtt.konvo.security;

import com.vulkantechtt.konvo.common.SafeText;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final String appBaseUrl;

    public OAuth2LoginFailureHandler(@Value("${konvo.public-base-url.app}") String appBaseUrl) {
        String base = SafeText.singleLine(appBaseUrl, "http://localhost:4200", 300);
        this.appBaseUrl = base.replaceAll("/$", "");
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        var httpSession = request.getSession(false);
        if (httpSession != null) {
            httpSession.invalidate();
        }
        response.sendRedirect(appBaseUrl + "/login?reason=google_auth_failed");
    }
}
