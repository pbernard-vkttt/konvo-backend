package com.vulkantechtt.konvo.conversations;

import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.conversations.dto.AssignRequest;
import com.vulkantechtt.konvo.conversations.dto.ConversationDetail;
import com.vulkantechtt.konvo.conversations.dto.ConversationSummary;
import com.vulkantechtt.konvo.conversations.dto.UpdateStatusRequest;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
@PreAuthorize("isAuthenticated()")
public class ConversationController {

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ConversationSummary> list(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @RequestParam(required = false) ConversationStatus status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return service.list(principal, status, search, pageable);
    }

    @GetMapping("/{id}")
    public ConversationDetail get(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @PathVariable UUID id) {
        return service.get(principal, id);
    }

    @PatchMapping("/{id}/status")
    public ConversationDetail updateStatus(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest req) {
        return service.updateStatus(principal, id, req.status());
    }

    @PatchMapping("/{id}/assignee")
    public ConversationDetail assign(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @PathVariable UUID id,
            @RequestBody AssignRequest req) {
        return service.assign(principal, id, req.assignedUserId());
    }

}
