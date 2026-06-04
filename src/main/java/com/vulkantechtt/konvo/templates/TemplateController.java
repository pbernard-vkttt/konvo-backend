package com.vulkantechtt.konvo.templates;

import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.templates.dto.CreateTemplateRequest;
import com.vulkantechtt.konvo.templates.dto.SendTemplateRequest;
import com.vulkantechtt.konvo.templates.dto.TemplateResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates")
@PreAuthorize("isAuthenticated()")
public class TemplateController {

    private final TemplateService service;

    public TemplateController(TemplateService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TemplateResponse> list(
            @AuthenticationPrincipal KonvoPrincipal principal,
            Pageable pageable) {
        return service.list(principal, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public TemplateResponse create(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @Valid @RequestBody CreateTemplateRequest req) {
        return service.create(principal, req);
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SyncResponse sync(@AuthenticationPrincipal KonvoPrincipal principal) {
        int count = service.syncFromMeta(principal);
        return new SyncResponse(count);
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','AGENT')")
    public TemplateResponse send(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody SendTemplateRequest req) {
        return service.send(principal, id, req);
    }

    public record SyncResponse(int synced) {}
}
