package com.vulkantechtt.konvo.knowledge;

import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.knowledge.dto.CreateTextSourceRequest;
import com.vulkantechtt.konvo.knowledge.dto.KnowledgeSourceResponse;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge/sources")
@PreAuthorize("isAuthenticated()")
public class KnowledgeController {

    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<KnowledgeSourceResponse> list(
            @AuthenticationPrincipal KonvoPrincipal principal,
            Pageable pageable) {
        return service.list(principal, pageable);
    }

    @GetMapping("/{id}")
    public KnowledgeSourceResponse get(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @PathVariable UUID id) {
        return service.get(principal, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public KnowledgeSourceResponse create(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @Valid @RequestBody CreateTextSourceRequest req) {
        return service.createText(principal, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public void delete(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @PathVariable UUID id) {
        service.delete(principal, id);
    }
}
