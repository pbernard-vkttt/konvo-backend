package com.vulkantechtt.konvo.templates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.audit.AuditAction;
import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.channels.Channel;
import com.vulkantechtt.konvo.channels.ChannelProvider;
import com.vulkantechtt.konvo.channels.ChannelRepository;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.conversations.ConversationRepository;
import com.vulkantechtt.konvo.conversations.MessageRepository;
import com.vulkantechtt.konvo.customers.CustomerRepository;
import com.vulkantechtt.konvo.realtime.SseHub;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.templates.dto.CreateTemplateRequest;
import com.vulkantechtt.konvo.users.Role;
import com.vulkantechtt.konvo.whatsapp.WhatsAppProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock MessageTemplateRepository templates;
    @Mock ChannelRepository channels;
    @Mock ConversationRepository conversations;
    @Mock CustomerRepository customers;
    @Mock MessageRepository messages;
    @Mock WhatsAppProvider provider;
    @Mock SseHub sseHub;
    @Mock AuditService audit;

    private TemplateService service;

    @BeforeEach
    void setUp() {
        service = new TemplateService(
                templates,
                channels,
                conversations,
                customers,
                messages,
                provider,
                sseHub,
                audit,
                new ObjectMapper());
    }

    @Test
    void createTemplateSubmitsToMetaAndPersistsPendingTemplate() {
        UUID tenantId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();

        Channel channel = new Channel();
        channel.setId(channelId);
        channel.setTenantId(tenantId);
        channel.setProvider(ChannelProvider.whatsapp_meta);
        channel.setWabaId("waba-123");

        when(channels.findByTenantId(tenantId)).thenReturn(List.of(channel));
        when(templates.findByTenantIdAndNameAndLanguage(tenantId, "promo_offer", "en_US"))
                .thenReturn(Optional.empty());
        when(provider.createTemplate(any())).thenReturn(new WhatsAppProvider.CreateTemplateResult("meta-42", "PENDING"));
        when(templates.save(any(MessageTemplate.class))).thenAnswer(invocation -> {
            MessageTemplate row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(UUID.randomUUID());
            }
            return row;
        });

        var response = service.create(principal(tenantId), new CreateTemplateRequest(
                "promo_offer",
                "en_US",
                TemplateCategory.marketing,
                "Our {{1}} is on",
                List.of("June sale"),
                "Use code {{1}} before {{2}}.",
                List.of("SAVE20", "Friday"),
                "Reply stop to opt out"));

        assertThat(response.name()).isEqualTo("promo_offer");
        assertThat(response.status()).isEqualTo(TemplateStatus.pending);
        assertThat(response.language()).isEqualTo("en_US");
        assertThat(response.components()).contains("\"type\":\"BODY\"");
        assertThat(response.components()).contains("\"header_text\":[\"June sale\"]");
        assertThat(response.components()).contains("\"body_text\":[[\"SAVE20\",\"Friday\"]]");

        ArgumentCaptor<WhatsAppProvider.CreateTemplateCommand> cmd = ArgumentCaptor.forClass(WhatsAppProvider.CreateTemplateCommand.class);
        verify(provider).createTemplate(cmd.capture());
        assertThat(cmd.getValue().channelId()).isEqualTo(channelId);
        assertThat(cmd.getValue().name()).isEqualTo("promo_offer");
        assertThat(cmd.getValue().language()).isEqualTo("en_US");
        assertThat(cmd.getValue().category()).isEqualTo("MARKETING");
        assertThat(cmd.getValue().components()).hasSize(3);

        verify(audit).record(any(), eq(AuditAction.TEMPLATE_CREATED), any(),
                eq("Created template promo_offer and submitted it to Meta for approval"),
                any(Map.class));
    }

    @Test
    void createTemplateRejectsMismatchedBodyExamples() {
        UUID tenantId = UUID.randomUUID();
        Channel channel = new Channel();
        channel.setId(UUID.randomUUID());
        channel.setTenantId(tenantId);
        channel.setProvider(ChannelProvider.whatsapp_meta);

        when(channels.findByTenantId(tenantId)).thenReturn(List.of(channel));
        when(templates.findByTenantIdAndNameAndLanguage(tenantId, "promo_offer", "en_US"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(principal(tenantId), new CreateTemplateRequest(
                "promo_offer",
                "en_US",
                TemplateCategory.marketing,
                null,
                List.of(),
                "Use code {{1}} before {{2}}.",
                List.of("SAVE20"),
                null)))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("Body examples");

        verify(provider, never()).createTemplate(any());
        verify(templates, never()).save(any());
    }

    @Test
    void createTemplateRequiresConnectedWhatsAppChannel() {
        UUID tenantId = UUID.randomUUID();
        when(channels.findByTenantId(tenantId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(principal(tenantId), new CreateTemplateRequest(
                "promo_offer",
                "en_US",
                TemplateCategory.marketing,
                null,
                List.of(),
                "Use code SAVE20.",
                List.of(),
                null)))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("Connect a WhatsApp channel");
    }

    private static KonvoPrincipal principal(UUID tenantId) {
        return new KonvoPrincipal(UUID.randomUUID(), "owner@example.com", "Owner", tenantId, Role.OWNER);
    }
}
