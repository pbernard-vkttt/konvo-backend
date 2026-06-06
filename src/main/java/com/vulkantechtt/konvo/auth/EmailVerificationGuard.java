package com.vulkantechtt.konvo.auth;

import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Central policy for features that require a confirmed mailbox before writes.
 */
@Component
public class EmailVerificationGuard {

    private final UserRepository users;

    public EmailVerificationGuard(UserRepository users) {
        this.users = users;
    }

    public void requireVerified(KonvoPrincipal principal) {
        if (principal == null || !users.existsByIdAndEmailVerifiedTrue(principal.userId())) {
            throw new KonvoException(
                    HttpStatus.FORBIDDEN,
                    "email_verification_required",
                    "Verify your email to continue.");
        }
    }
}
