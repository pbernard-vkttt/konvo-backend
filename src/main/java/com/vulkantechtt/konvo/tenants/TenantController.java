package com.vulkantechtt.konvo.tenants;

import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.tenants.dto.TenantResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
@PreAuthorize("isAuthenticated()")
public class TenantController {

    private final TenantRepository tenantRepository;

    public TenantController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @GetMapping("/me")
    public TenantResponse me(@AuthenticationPrincipal KonvoPrincipal principal) {
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> KonvoException.notFound("Tenant", principal.tenantId()));
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getCountryCode(),
                tenant.getPlan(),
                tenant.getStatus().name(),
                tenant.getCreatedAt());
    }
}
