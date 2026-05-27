package com.vulkantechtt.konvo.audit;

import com.vulkantechtt.konvo.audit.dto.AuditEntry;
import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public PageResponse<AuditEntry> list(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @PageableDefault(size = 50) Pageable pageable) {
        return audit.list(principal.tenantId(), action, entityType, pageable);
    }
}
