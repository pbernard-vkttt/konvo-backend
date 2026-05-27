package com.vulkantechtt.konvo.conversations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.channels.ChannelRepository;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.customers.CustomerRepository;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.Role;
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
    void crossTenantRejected() {
        UUID convId = UUID.randomUUID();
        Conversation c = build(UUID.randomUUID(), null);
        when(conversations.findById(convId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.requireVisibleConversation(principal(UUID.randomUUID(), Role.OWNER), convId))
                .isInstanceOf(KonvoException.class);
    }

    private KonvoPrincipal principal(UUID userId, Role role) {
        return new KonvoPrincipal(userId, "u@x.tt", "U", tenantId, role);
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
