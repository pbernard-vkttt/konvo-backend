package com.vulkantechtt.konvo.channels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.auth.EmailVerificationGuard;
import com.vulkantechtt.konvo.channels.dto.ConnectWhatsAppRequest;
import com.vulkantechtt.konvo.channels.dto.EmbeddedSignupRequest;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.Role;
import com.vulkantechtt.konvo.whatsapp.WhatsAppOnboardingGateway;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

    @Mock ChannelRepository channels;
    @Mock AuditService audit;
    @Mock EmailVerificationGuard emailVerification;
    @Mock ObjectProvider<WhatsAppOnboardingGateway> onboarding;
    @Mock WhatsAppOnboardingGateway gateway;
    private ChannelService service;

    @BeforeEach
    void setUp() {
        service = new ChannelService(channels, audit, emailVerification, onboarding, "http://api.test");
    }

    private static KonvoPrincipal principal(UUID tenantId) {
        return new KonvoPrincipal(UUID.randomUUID(), "actor@x.tt", "Actor", tenantId, Role.OWNER);
    }

    @Test
    void rejectsSecondWhatsAppChannel() {
        UUID tenantId = UUID.randomUUID();
        when(channels.existsByTenantIdAndProvider(tenantId, ChannelProvider.whatsapp_meta))
                .thenReturn(true);

        assertThatThrownBy(() -> service.connectWhatsApp(principal(tenantId), sampleRequest()))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("already has a WhatsApp channel");
        verify(channels, never()).save(any());
    }

    @Test
    void connectWhatsAppRequiresVerifiedEmail() {
        UUID tenantId = UUID.randomUUID();
        KonvoPrincipal actor = principal(tenantId);
        doThrow(new KonvoException(org.springframework.http.HttpStatus.FORBIDDEN,
                "email_verification_required", "Verify your email to continue."))
                .when(emailVerification).requireVerified(actor);

        assertThatThrownBy(() -> service.connectWhatsApp(actor, sampleRequest()))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("Verify your email");
        verify(channels, never()).existsByTenantIdAndProvider(any(), any());
    }

    @Test
    void rejectsDuplicatePhoneNumberId() {
        UUID tenantId = UUID.randomUUID();
        when(channels.existsByTenantIdAndProvider(tenantId, ChannelProvider.whatsapp_meta))
                .thenReturn(false);
        Channel existing = new Channel();
        existing.setTenantId(UUID.randomUUID());
        when(channels.findByPhoneNumberId("12345"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.connectWhatsApp(principal(tenantId), sampleRequest()))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("connected to another workspace");
    }

    @Test
    void rejectsCrossTenantLookup() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantChannelId = UUID.randomUUID();
        Channel foreign = new Channel();
        foreign.setTenantId(UUID.randomUUID());
        when(channels.findById(otherTenantChannelId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.get(tenantId, otherTenantChannelId))
                .isInstanceOf(KonvoException.class);
    }

    @Test
    void buildsWebhookUrlFromApiBase() {
        UUID tenantId = UUID.randomUUID();
        Channel saved = new Channel();
        saved.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        saved.setTenantId(tenantId);
        saved.setProvider(ChannelProvider.whatsapp_meta);
        saved.setDisplayName("Doubles King");
        saved.setStatus(ChannelStatus.connected);
        saved.setPhoneNumberId("12345");
        saved.setWebhookVerifyToken("vt");
        when(channels.findById(saved.getId())).thenReturn(Optional.of(saved));

        var resp = service.get(tenantId, saved.getId());

        assertThat(resp.webhookUrl())
                .isEqualTo("http://api.test/api/webhooks/meta/11111111-1111-1111-1111-111111111111");
    }

    @Test
    void embeddedSignupExchangesCodeAndSavesChannel() {
        UUID tenantId = UUID.randomUUID();
        when(channels.existsByTenantIdAndProvider(tenantId, ChannelProvider.whatsapp_meta))
                .thenReturn(false);
        when(channels.findByPhoneNumberId("12345")).thenReturn(Optional.empty());
        when(onboarding.getIfAvailable()).thenReturn(gateway);
        when(gateway.completeSignup("auth-code", "12345", "67890"))
                .thenReturn(new WhatsAppOnboardingGateway.OnboardingResult(
                        "EAAtoken", "app-secret", "+18681234567", "Doubles King"));
        when(channels.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.connectWhatsAppViaEmbeddedSignup(
                principal(tenantId),
                new EmbeddedSignupRequest("auth-code", "12345", "67890", null));

        ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
        verify(channels).save(captor.capture());
        Channel saved = captor.getValue();
        assertThat(saved.getProvider()).isEqualTo(ChannelProvider.whatsapp_meta);
        assertThat(saved.getDisplayName()).isEqualTo("Doubles King");
        assertThat(saved.getPhoneNumber()).isEqualTo("+18681234567");
        assertThat(saved.getAccessToken()).isEqualTo("EAAtoken");
        assertThat(saved.getAppSecret()).isEqualTo("app-secret");
        assertThat(saved.getStatus()).isEqualTo(ChannelStatus.connected);
    }

    @Test
    void embeddedSignupRejectedWhenGatewayUnavailable() {
        UUID tenantId = UUID.randomUUID();
        when(channels.existsByTenantIdAndProvider(tenantId, ChannelProvider.whatsapp_meta))
                .thenReturn(false);
        when(channels.findByPhoneNumberId("12345")).thenReturn(Optional.empty());
        when(onboarding.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.connectWhatsAppViaEmbeddedSignup(
                principal(tenantId),
                new EmbeddedSignupRequest("auth-code", "12345", "67890", null)))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("not available");
        verify(channels, never()).save(any());
    }

    private static ConnectWhatsAppRequest sampleRequest() {
        return new ConnectWhatsAppRequest(
                "Doubles King", "+18681234567", "12345", "67890",
                "app-secret", "EAAGtest");
    }
}
