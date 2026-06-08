package com.vulkantechtt.konvo.account;

import com.vulkantechtt.konvo.account.dto.AccountProfileResponse;
import com.vulkantechtt.konvo.account.dto.ChangePasswordRequest;
import com.vulkantechtt.konvo.account.dto.UpdateProfileRequest;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service account endpoints for the currently authenticated user.
 * Accessible to all authenticated roles — not OWNER/ADMIN restricted.
 *
 * GET  /api/v1/account/profile   — read own profile
 * PATCH /api/v1/account/profile  — update display name
 * PUT  /api/v1/account/password  — change password
 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/profile")
    public AccountProfileResponse getProfile(
            @AuthenticationPrincipal KonvoPrincipal principal) {
        return accountService.getProfile(principal);
    }

    @PatchMapping("/profile")
    public AccountProfileResponse updateProfile(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest req) {
        return accountService.updateProfile(principal, req);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest req) {
        accountService.changePassword(principal, req);
        return ResponseEntity.noContent().build();
    }
}
