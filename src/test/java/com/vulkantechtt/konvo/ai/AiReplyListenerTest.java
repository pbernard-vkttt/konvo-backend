package com.vulkantechtt.konvo.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.billing.SubscriptionService;
import com.vulkantechtt.konvo.billing.UsageService;
import com.vulkantechtt.konvo.conversations.Conversation;
import com.vulkantechtt.konvo.conversations.ConversationRepository;
import com.vulkantechtt.konvo.conversations.Message;
import com.vulkantechtt.konvo.conversations.MessageDirection;
import com.vulkantechtt.konvo.conversations.MessageRepository;
import com.vulkantechtt.konvo.conversations.OutboundMessageCommand;
import com.vulkantechtt.konvo.conversations.OutboundMessageDispatcher;
import com.vulkantechtt.konvo.customers.Customer;
import com.vulkantechtt.konvo.customers.CustomerRepository;
import com.vulkantechtt.konvo.knowledge.KnowledgeRetriever;
import com.vulkantechtt.konvo.notifications.NotificationService;
import com.vulkantechtt.konvo.tenants.Tenant;
import com.vulkantechtt.konvo.tenants.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AiReplyListenerTest {

    @Mock AiCompletionProvider completion;
    @Mock KnowledgeRetriever retriever;
    @Mock ConversationRepository conversations;
    @Mock MessageRepository messages;
    @Mock CustomerRepository customers;
    @Mock TenantRepository tenants;
    @Mock OutboundMessageDispatcher dispatcher;
    @Mock AiRunRecorder runs;
    @Mock SubscriptionService subscriptions;
    @Mock UsageService usage;
    @Mock NotificationService notifications;

    private AiReplyListener listener;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID inboundMessageId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new AiReplyListener(
                completion,
                retriever,
                conversations,
                messages,
                customers,
                tenants,
                dispatcher,
                runs,
                subscriptions,
                usage,
                notifications);
        when(completion.name()).thenReturn("stub");
    }

    @Test
    void includesConfiguredPreviousMessagesAsCompletionTurns() {
        Instant currentSentAt = Instant.parse("2026-06-04T10:00:00Z");
        wireConversationCustomerTenant(2);
        Message current = message(inboundMessageId, MessageDirection.inbound, "Current question", currentSentAt);
        Message olderCustomer = message(UUID.randomUUID(), MessageDirection.inbound, "Earlier customer question", currentSentAt.minusSeconds(120));
        Message olderReply = message(UUID.randomUUID(), MessageDirection.outbound, "Earlier team reply", currentSentAt.minusSeconds(60));
        when(messages.findById(inboundMessageId)).thenReturn(Optional.of(current));
        when(messages.findByConversationIdAndSentAtBeforeOrderBySentAtDesc(
                eq(conversationId), eq(currentSentAt), any(Pageable.class)))
                .thenReturn(List.of(olderReply, olderCustomer));
        when(retriever.topK(tenantId, "Current question", 4)).thenReturn(List.of());
        when(completion.complete(any())).thenReturn(new AiCompletionProvider.Completion("Sure.", 10, 2, "stub-v1", 0));

        listener.onAiReply(command("Current question"));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(messages).findByConversationIdAndSentAtBeforeOrderBySentAtDesc(
                eq(conversationId), eq(currentSentAt), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);

        ArgumentCaptor<AiCompletionProvider.CompletionRequest> request =
                ArgumentCaptor.forClass(AiCompletionProvider.CompletionRequest.class);
        verify(completion).complete(request.capture());
        assertThat(request.getValue().turns())
                .extracting(AiCompletionProvider.CompletionRequest.Turn::content)
                .containsExactly("Earlier customer question", "Earlier team reply");
        assertThat(request.getValue().turns())
                .extracting(AiCompletionProvider.CompletionRequest.Turn::role)
                .containsExactly(
                        AiCompletionProvider.CompletionRequest.Role.USER,
                        AiCompletionProvider.CompletionRequest.Role.ASSISTANT);
        verify(dispatcher).enqueue(any(OutboundMessageCommand.class));
    }

    @Test
    void memoryLimitZeroSkipsPreviousMessageLookup() {
        wireConversationCustomerTenant(0);
        when(retriever.topK(tenantId, "Current question", 4)).thenReturn(List.of());
        when(completion.complete(any())).thenReturn(new AiCompletionProvider.Completion("Sure.", 10, 2, "stub-v1", 0));

        listener.onAiReply(command("Current question"));

        verify(messages, never()).findById(any());
        ArgumentCaptor<AiCompletionProvider.CompletionRequest> request =
                ArgumentCaptor.forClass(AiCompletionProvider.CompletionRequest.class);
        verify(completion).complete(request.capture());
        assertThat(request.getValue().turns()).isEmpty();
    }

    private void wireConversationCustomerTenant(int memoryLimit) {
        Conversation conv = new Conversation();
        conv.setId(conversationId);
        conv.setTenantId(tenantId);
        conv.setChannelId(channelId);
        conv.setCustomerId(customerId);
        conv.setAutoReplyEnabled(true);
        when(conversations.findById(conversationId)).thenReturn(Optional.of(conv));

        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setTenantId(tenantId);
        customer.setPhone("+18681234567");
        when(customers.findById(customerId)).thenReturn(Optional.of(customer));

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Acme Co");
        tenant.setCustomerMemoryMessageLimit(memoryLimit);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    }

    private AiReplyCommand command(String body) {
        return new AiReplyCommand(tenantId, conversationId, channelId, customerId, inboundMessageId, body);
    }

    private Message message(UUID id, MessageDirection direction, String body, Instant sentAt) {
        Message m = new Message();
        m.setId(id);
        m.setTenantId(tenantId);
        m.setConversationId(conversationId);
        m.setDirection(direction);
        m.setContentType("text");
        m.setBody(body);
        m.setSentAt(sentAt);
        return m;
    }
}
