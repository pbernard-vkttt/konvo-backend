package com.vulkantechtt.konvo.tenants;

import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.tenants.dto.TenantResponse;
import com.vulkantechtt.konvo.tenants.dto.UpdateOnboardingWorkspaceRequest;
import com.vulkantechtt.konvo.tenants.dto.UpdateTenantSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
@PreAuthorize("isAuthenticated()")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/me")
    public TenantResponse me(@AuthenticationPrincipal KonvoPrincipal principal) {
        return tenantService.me(principal.tenantId());
    }

    @PatchMapping("/me/settings")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public TenantResponse updateSettings(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @Valid @RequestBody UpdateTenantSettingsRequest req) {
        return tenantService.updateSettings(principal, req);
    }

    @PatchMapping("/me/onboarding/workspace")
    @PreAuthorize("hasRole('OWNER')")
    public TenantResponse updateOnboardingWorkspace(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @Valid @RequestBody UpdateOnboardingWorkspaceRequest req) {
        return tenantService.updateOnboardingWorkspace(principal, req);
    }

    @PostMapping("/me/onboarding/complete")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<TenantResponse> completeOnboarding(
            @AuthenticationPrincipal KonvoPrincipal principal) {
        return ResponseEntity.ok(tenantService.completeOnboarding(principal));
    }
}
