package com.vulkantechtt.konvo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.auth.dto.ForgotPasswordRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock AuthService authService;
    @Mock AuthCookieWriter cookies;

    @Test
    void forgotPasswordDoesNotReturnRawTokenWhenDevTokenDisabled() {
        when(authService.beginPasswordReset("a@b.tt")).thenReturn("raw-token");
        AuthController controller = new AuthController(authService, cookies, false);

        var response = controller.forgot(new ForgotPasswordRequest("a@b.tt"));

        assertThat(response.getBody()).isEqualTo(new AuthController.ForgotPasswordResponse(true));
    }

    @Test
    void forgotPasswordCanReturnRawTokenWhenDevTokenEnabled() {
        when(authService.beginPasswordReset("a@b.tt")).thenReturn("raw-token");
        AuthController controller = new AuthController(authService, cookies, true);

        var response = controller.forgot(new ForgotPasswordRequest("a@b.tt"));

        assertThat(response.getBody()).isEqualTo(new AuthController.DevForgotPasswordResponse(true, "raw-token"));
    }
}
