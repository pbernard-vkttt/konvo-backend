package com.vulkantechtt.konvo.account;

import com.vulkantechtt.konvo.account.dto.AccountProfileResponse;
import com.vulkantechtt.konvo.account.dto.ChangePasswordRequest;
import com.vulkantechtt.konvo.account.dto.UpdateProfileRequest;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.User;
import com.vulkantechtt.konvo.users.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AccountProfileResponse getProfile(KonvoPrincipal principal) {
        User user = users.findById(principal.userId())
                .orElseThrow(() -> KonvoException.notFound("User", principal.userId()));
        return toResponse(user);
    }

    public AccountProfileResponse updateProfile(KonvoPrincipal principal, UpdateProfileRequest req) {
        User user = users.findById(principal.userId())
                .orElseThrow(() -> KonvoException.notFound("User", principal.userId()));
        user.setFullName(req.fullName().strip());
        return toResponse(users.save(user));
    }

    public void changePassword(KonvoPrincipal principal, ChangePasswordRequest req) {
        User user = users.findById(principal.userId())
                .orElseThrow(() -> KonvoException.notFound("User", principal.userId()));

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw KonvoException.badRequest(
                    "This account uses social login and does not have a password set");
        }
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw KonvoException.badRequest("Current password is incorrect");
        }
        if (req.newPassword().equals(req.currentPassword())) {
            throw KonvoException.badRequest("New password must differ from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        users.save(user);
    }

    private static AccountProfileResponse toResponse(User user) {
        return new AccountProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.isEmailVerified());
    }
}
