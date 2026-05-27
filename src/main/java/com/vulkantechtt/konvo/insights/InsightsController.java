package com.vulkantechtt.konvo.insights;

import com.vulkantechtt.konvo.insights.dto.InsightsSnapshot;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/insights")
@PreAuthorize("isAuthenticated()")
public class InsightsController {

    private final InsightsService insights;

    public InsightsController(InsightsService insights) {
        this.insights = insights;
    }

    @GetMapping
    public InsightsSnapshot snapshot(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @RequestParam(defaultValue = "14") int rangeDays) {
        return insights.snapshot(principal.tenantId(), rangeDays);
    }
}
