package com.vulkantechtt.konvo.scheduling;

import com.vulkantechtt.konvo.scheduling.dto.SchedulingSettingsResponse;
import com.vulkantechtt.konvo.scheduling.dto.UpdateSchedulingSettingsRequest;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scheduling/settings")
@PreAuthorize("isAuthenticated()")
public class SchedulingSettingsController {

    private final SchedulingService scheduling;

    public SchedulingSettingsController(SchedulingService scheduling) {
        this.scheduling = scheduling;
    }

    @GetMapping
    public SchedulingSettingsResponse get(@AuthenticationPrincipal KonvoPrincipal principal) {
        return scheduling.get(principal.tenantId());
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SchedulingSettingsResponse update(@AuthenticationPrincipal KonvoPrincipal principal,
                                             @Valid @RequestBody UpdateSchedulingSettingsRequest req) {
        return scheduling.update(principal, req);
    }
}
