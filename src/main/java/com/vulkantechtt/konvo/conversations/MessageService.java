package com.vulkantechtt.konvo.conversations;

import com.vulkantechtt.konvo.common.AfterCommit;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.conversations.dto.MessageResponse;
import com.vulkantechtt.konvo.conversations.dto.SendMessageRequest;
import com.vulkantechtt.konvo.customers.Customer;
import com.vulkantechtt.konvo.customers.CustomerRepository;
import com.vulkantechtt.konvo.realtime.SseHub;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the message thread for a conversation, and accepts agent replies.
 * Agent replies write a {@code queued} row inside the caller's transaction so
 * the inbox optimistic UI shows the bubble immediately; the actual Meta send
 * happens asynchronously in {@link OutboundSendListener}.
 */
@Service
public class MessageService {

    private final ConversationService conversationService;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final CustomerRepository customers;
    private final OutboundMessageDispatcher dispatcher;
    private final SseHub sseHub;

    public MessageService(
            ConversationService conversationService,
            ConversationRepository conversations,
            MessageRepository messages,
            CustomerRepository customers,
            OutboundMessageDispatcher dispatcher,
            SseHub sseHub) {
        this.conversationService = conversationService;
        this.conversations = conversations;
        this.messages = messages;
        this.customers = customers;
        this.dispatcher = dispatcher;
        this.sseHub = sseHub;
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> list(KonvoPrincipal principal, UUID conversationId, Pageable pageable) {
        conversationService.requireVisibleConversation(principal, conversationId);
        Page<Message> newestFirst = messages.findLatestByConversationId(conversationId, pageable);
        List<Message> chronological = new ArrayList<>(newestFirst.getContent());
        Collections.reverse(chronological);
        return new PageResponse<>(
                chronological.stream().map(MessageService::toResponse).toList(),
                newestFirst.getNumber(),
                newestFirst.getSize(),
                newestFirst.getTotalElements(),
                newestFirst.getTotalPages(),
                newestFirst.hasNext());
    }

    @Transactional
    public MessageResponse sendAgentReply(KonvoPrincipal principal, UUID conversationId, SendMessageRequest req) {
        Conversation conv = conversationService.requireVisibleConversation(principal, conversationId);
        if (conv.getStatus() == ConversationStatus.closed) {
            throw KonvoException.badRequest("Reopen this conversation before replying");
        }
        // WhatsApp only allows free-form replies within 24h of the customer's
        // last inbound message; outside that the agent must use a template.
        conversationService.assertWhatsAppWindowOpen(conv);
        Customer customer = customers.findById(conv.getCustomerId())
                .orElseThrow(() -> KonvoException.notFound("Customer", conv.getCustomerId()));

        Message msg = new Message();
        msg.setTenantId(conv.getTenantId());
        msg.setConversationId(conv.getId());
        msg.setDirection(MessageDirection.outbound);
        msg.setContentType("text");
        msg.setBody(req.body());
        msg.setStatus(MessageStatus.queued);
        msg.setSenderType(SenderType.agent);
        msg.setSenderName(principal.fullName());
        msg.setSentAt(Instant.now());
        messages.save(msg);

        conv.setLastMessageAt(msg.getSentAt());
        conv.setLastMessagePreview(req.body().length() > 280 ? req.body().substring(0, 280) : req.body());
        // Auto-assign on first reply so the inbox "owned by me" filter is useful.
        if (conv.getAssignedUserId() == null) {
            conv.setAssignedUserId(principal.userId());
        }
        conversations.save(conv);

        UUID tenantId = conv.getTenantId();
        UUID messageId = msg.getId();
        UUID channelId = conv.getChannelId();
        UUID customerId = customer.getId();
        String phone = customer.getPhone();

        // Durable hand-off: the outbound command is written to the outbox in
        // this same transaction, so it commits atomically with the queued
        // Message row and survives a broker outage (audit H-1).
        dispatcher.enqueue(new OutboundMessageCommand(
                messageId, tenantId, conversationId, channelId, customerId, phone, req.body(),
                SenderType.agent, principal.fullName()));

        // SSE is best-effort optimistic UI and stays post-commit.
        AfterCommit.run(() -> {
            sseHub.broadcast(tenantId, "message_appended",
                    java.util.Map.of("conversationId", conversationId.toString(),
                            "messageId", messageId.toString()));
            sseHub.broadcast(tenantId, "conversation_updated",
                    java.util.Map.of("conversationId", conversationId.toString()));
        });

        return toResponse(msg);
    }

    static MessageResponse toResponse(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getConversationId(),
                m.getDirection(),
                m.getContentType(),
                m.getBody(),
                m.getStatus(),
                m.getSenderType(),
                m.getSenderName(),
                m.getSentAt(),
                m.getDeliveredAt(),
                m.getReadAt(),
                m.getErrorCode(),
                m.getErrorMessage());
    }
}
