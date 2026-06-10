package com.vulkantechtt.konvo.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.conversations.dto.MessageResponse;
import com.vulkantechtt.konvo.customers.CustomerRepository;
import com.vulkantechtt.konvo.realtime.SseHub;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock ConversationService conversationService;
    @Mock ConversationRepository conversations;
    @Mock MessageRepository messages;
    @Mock CustomerRepository customers;
    @Mock OutboundMessageDispatcher dispatcher;
    @Mock SseHub sseHub;

    @InjectMocks MessageService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @Test
    void listReturnsLatestPageInChronologicalOrder() {
        Pageable pageable = PageRequest.of(0, 2);
        Message newest = message("2026-06-10T12:00:00Z");
        Message older = message("2026-06-10T11:00:00Z");
        when(messages.findLatestByConversationId(conversationId, pageable))
                .thenReturn(new PageImpl<>(List.of(newest, older), pageable, 3));

        PageResponse<MessageResponse> page = service.list(principal(), conversationId, pageable);

        assertThat(page.content()).extracting(MessageResponse::id)
                .containsExactly(older.getId(), newest.getId());
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();

        InOrder order = inOrder(conversationService, messages);
        order.verify(conversationService).requireVisibleConversation(principal(), conversationId);
        order.verify(messages).findLatestByConversationId(conversationId, pageable);
    }

    @Test
    void listReturnsOlderPagesChronologically() {
        Pageable pageable = PageRequest.of(1, 2);
        Message oldest = message("2026-06-10T10:00:00Z");
        when(messages.findLatestByConversationId(conversationId, pageable))
                .thenReturn(new PageImpl<>(List.of(oldest), pageable, 3));

        PageResponse<MessageResponse> page = service.list(principal(), conversationId, pageable);

        assertThat(page.content()).extracting(MessageResponse::id)
                .containsExactly(oldest.getId());
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
    }

    private KonvoPrincipal principal() {
        return new KonvoPrincipal(userId, "agent@example.com", "Agent", tenantId, Role.AGENT);
    }

    private Message message(String sentAt) {
        Message msg = new Message();
        msg.setId(UUID.randomUUID());
        msg.setTenantId(tenantId);
        msg.setConversationId(conversationId);
        msg.setDirection(MessageDirection.inbound);
        msg.setContentType("text");
        msg.setBody(sentAt);
        msg.setStatus(MessageStatus.received);
        msg.setSentAt(Instant.parse(sentAt));
        return msg;
    }
}
