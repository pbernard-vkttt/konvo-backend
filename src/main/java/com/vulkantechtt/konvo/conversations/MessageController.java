package com.vulkantechtt.konvo.conversations;

import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.conversations.dto.MessageResponse;
import com.vulkantechtt.konvo.conversations.dto.SendMessageRequest;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/messages")
@PreAuthorize("isAuthenticated()")
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<MessageResponse> list(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @PathVariable UUID conversationId,
            Pageable pageable) {
        return service.list(principal, conversationId, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(
            @AuthenticationPrincipal KonvoPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest req) {
        return service.sendAgentReply(principal, conversationId, req);
    }
}
