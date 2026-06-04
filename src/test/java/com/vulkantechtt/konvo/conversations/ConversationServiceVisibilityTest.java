package com.vulkantechtt.konvo.conversations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.channels.Channel;
import com.vulkantechtt.konvo.channels.ChannelProvider;
import com.vulkantechtt.konvo.channels.ChannelRepository;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.customers.CustomerRepository;
import com.vulkantechtt.konvo.notifications.NotificationService;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationServiceVisibilityTest {

    @Mock ConversationRepository conversations;
    @Mock CustomerRepository customers;
    @Mock ChannelRepository channels;
    @Mock MessageRepository messages;
    @Mock NotificationService notifications;

    @InjectMocks ConversationService service;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void agentCanSeeOwnAssignedConversation() {
        UUID agentId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation c = build(tenantId, agentId);
        when(conversations.findById(convId)).thenReturn(Optional.of(c));

        assertThatCode(() -> service.requireVisibleConversation(principal(agentId, Role.AGENT), convId))
                .doesNotThrowAnyException();
    }

    @Test
    void agentCanSeeUnassignedConversation() {
        UUID agentId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation c = build(tenantId, null);
        when(conversations.findById(convId)).thenReturn(Optional.of(c));

        assertThatCode(() -> service.requireVisibleConversation(principal(agentId, Role.AGENT), convId))
                .doesNotThrowAnyException();
    }

    @Test
    void agentBlockedFromOtherAgentConversation() {
        UUID agentId = UUID.randomUUID();
        UUID otherAgent = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation c = build(tenantId, otherAgent);
        when(conversations.findById(convId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.requireVisibleConversation(principal(agentId, Role.AGENT), convId))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("assigned to another agent");
    }

    @Test
    void ownerSeesEverythingInTenant() {
        UUID ownerId = UUID.randomUUID();
        UUID otherAgent = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation c = build(tenantId, otherAgent);
        when(conversations.findById(convId)).thenReturn(Optional.of(c));

        assertThatCode(() -> service.requireVisibleConversation(principal(ownerId, Role.OWNER), convId))
                .doesNotThrowAnyException();
    }

    @Test
    void managerSeesEverythingInTenant() {
        UUID managerId = UUID.randomUUID();
        UUID otherAgent = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation c = build(tenantId, otherAgent);
        when(conversations.findById(convId)).thenReturn(Optional.of(c));

        assertThatCode(() -> service.requireVisibleConversation(principal(managerId, Role.MANAGER), convId))
                .doesNotThrowAnyException();
    }

    @Test
    void managerCanAssignAnotherAgent() {
        UUID managerId = UUID.randomUUID();
        UUID targetAgent = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation c = build(tenantId, null);
        when(conversations.findById(convId)).thenReturn(Optional.of(c));

        assertThatCode(() -> service.assign(principal(managerId, Role.MANAGER), convId, targetAgent))
                .doesNotThrowAnyException();
    }

    @Test
    void agentCannotAssignAnotherAgent() {
        UUID agentId = UUID.randomUUID();
        UUID targetAgent = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation c = build(tenantId, null);
        when(conversations.findById(convId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.assign(principal(agentId, Role.AGENT), convId, targetAgent))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("assign other agents");
    }

    @Test
    void crossTenantRejected() {
        UUID convId = UUID.randomUUID();
        Conversation c = build(UUID.randomUUID(), null);
        when(conversations.findById(convId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.requireVisibleConversation(principal(UUID.randomUUID(), Role.OWNER), convId))
                .isInstanceOf(KonvoException.class);
    }

    @Test
    void whatsAppWindowOpenWhenInboundWithin24h() {
        Conversation c = build(tenantId, null);
        when(channels.findById(c.getChannelId())).thenReturn(Optional.of(whatsappChannel()));
        when(messages.findFirstByConversationIdAndDirectionOrderBySentAtDesc(c.getId(), MessageDirection.inbound))
                .thenReturn(Optional.of(inboundAt(c.getId(), Instant.now().minus(Duration.ofHours(1)))));

        assertThatCode(() -> service.assertWhatsAppWindowOpen(c)).doesNotThrowAnyException();
    }

    @Test
    void whatsAppWindowClosedWhenInboundOlderThan24h() {
        Conversation c = build(tenantId, null);
        when(channels.findById(c.getChannelId())).thenReturn(Optional.of(whatsappChannel()));
        when(messages.findFirstByConversationIdAndDirectionOrderBySentAtDesc(c.getId(), MessageDirection.inbound))
                .thenReturn(Optional.of(inboundAt(c.getId(), Instant.now().minus(Duration.ofHours(25)))));

        assertThatThrownBy(() -> service.assertWhatsAppWindowOpen(c))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("24-hour WhatsApp");
    }

    @Test
    void whatsAppWindowClosedWhenNoInboundYet() {
        Conversation c = build(tenantId, null);
        when(channels.findById(c.getChannelId())).thenReturn(Optional.of(whatsappChannel()));
        when(messages.findFirstByConversationIdAndDirectionOrderBySentAtDesc(c.getId(), MessageDirection.inbound))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assertWhatsAppWindowOpen(c))
                .isInstanceOf(KonvoException.class);
    }

    private KonvoPrincipal principal(UUID userId, Role role) {
        return new KonvoPrincipal(userId, "u@x.tt", "U", tenantId, role);
    }

    private static Channel whatsappChannel() {
        Channel ch = new Channel();
        ch.setProvider(ChannelProvider.whatsapp_meta);
        return ch;
    }

    private static Message inboundAt(UUID conversationId, Instant at) {
        Message m = new Message();
        m.setConversationId(conversationId);
        m.setDirection(MessageDirection.inbound);
        m.setSentAt(at);
        return m;
    }

    private static Conversation build(UUID tenantId, UUID assignedUserId) {
        Conversation c = new Conversation();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setChannelId(UUID.randomUUID());
        c.setCustomerId(UUID.randomUUID());
        c.setStatus(ConversationStatus.open);
        c.setAssignedUserId(assignedUserId);
        return c;
    }
}
