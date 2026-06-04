package com.vulkantechtt.konvo.insights;

import com.vulkantechtt.konvo.insights.dto.InsightsSnapshot;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<InsightsSnapshot> snapshot(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @RequestParam(defaultValue = "14") int rangeDays) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePrivate())
                .body(insights.snapshot(principal.tenantId(), rangeDays));
    }
}
