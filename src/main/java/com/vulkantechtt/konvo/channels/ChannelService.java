package com.vulkantechtt.konvo.channels;

import com.vulkantechtt.konvo.audit.AuditAction;
import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.auth.TokenHasher;
import com.vulkantechtt.konvo.channels.dto.ChannelResponse;
import com.vulkantechtt.konvo.channels.dto.ConnectWhatsAppRequest;
import com.vulkantechtt.konvo.channels.dto.UpdateChannelRequest;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped channel CRUD. The webhook URL exposed to the UI is built from
 * the configured API base URL plus the channel id, so a tenant can paste
 * something like {@code https://api.konvelo.io/api/webhooks/meta/{id}} into the
 * Meta dashboard. The webhook verify token is generated server-side and shown
 * to the user once (it's safe to re-show because it's used for the GET
 * challenge, not for signature verification — that's the app_secret).
 */
@Service
public class ChannelService {

    private final ChannelRepository channels;
    private final AuditService audit;
    private final String apiBaseUrl;

    public ChannelService(ChannelRepository channels,
                          AuditService audit,
                          @Value("${konvo.public-base-url.api}") String apiBaseUrl) {
        this.channels = channels;
        this.audit = audit;
        this.apiBaseUrl = trimTrailingSlash(apiBaseUrl);
    }

    @Transactional(readOnly = true)
    public List<ChannelResponse> list(UUID tenantId) {
        return channels.findByTenantId(tenantId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ChannelResponse get(UUID tenantId, UUID id) {
        Channel ch = requireOwned(tenantId, id);
        return toResponse(ch);
    }

    @Transactional
    public ChannelResponse connectWhatsApp(KonvoPrincipal actor, ConnectWhatsAppRequest req) {
        UUID tenantId = actor.tenantId();
        if (channels.existsByTenantIdAndProvider(tenantId, ChannelProvider.whatsapp_meta)) {
            throw KonvoException.conflict("This workspace already has a WhatsApp channel connected");
        }
        channels.findByPhoneNumberId(req.phoneNumberId()).ifPresent(c -> {
            throw KonvoException.conflict("That WhatsApp number is already connected to another workspace");
        });

        Channel ch = new Channel();
        ch.setTenantId(tenantId);
        ch.setProvider(ChannelProvider.whatsapp_meta);
        ch.setDisplayName(req.displayName());
        ch.setPhoneNumber(req.phoneNumber());
        ch.setPhoneNumberId(req.phoneNumberId());
        ch.setWabaId(req.wabaId());
        ch.setAppSecret(req.appSecret());
        ch.setAccessToken(req.accessToken());
        ch.setWebhookVerifyToken(TokenHasher.randomToken());
        ch.setStatus(ChannelStatus.connected);
        Channel saved = channels.save(ch);
        audit.record(actor, AuditAction.CHANNEL_CONNECTED, saved.getId(),
                "Connected WhatsApp channel " + saved.getDisplayName(),
                java.util.Map.of("displayName", saved.getDisplayName(),
                        "phoneNumber", saved.getPhoneNumber(),
                        "wabaId", saved.getWabaId() == null ? "" : saved.getWabaId()));
        return toResponse(saved, true);
    }

    @Transactional
    public ChannelResponse update(KonvoPrincipal actor, UUID id, UpdateChannelRequest req) {
        UUID tenantId = actor.tenantId();
        Channel ch = requireOwned(tenantId, id);
        String previousName = ch.getDisplayName();
        ch.setDisplayName(req.displayName());
        if (req.accessToken() != null && !req.accessToken().isBlank()) {
            ch.setAccessToken(req.accessToken());
        }
        if (req.appSecret() != null && !req.appSecret().isBlank()) {
            ch.setAppSecret(req.appSecret());
        }
        Channel saved = channels.save(ch);
        audit.record(actor, AuditAction.CHANNEL_UPDATED, saved.getId(),
                "Updated channel " + saved.getDisplayName(),
                java.util.Map.of("from", previousName, "to", saved.getDisplayName()));
        return toResponse(saved);
    }

    @Transactional
    public void disconnect(KonvoPrincipal actor, UUID id) {
        UUID tenantId = actor.tenantId();
        Channel ch = requireOwned(tenantId, id);
        channels.delete(ch);
        audit.record(actor, AuditAction.CHANNEL_DISCONNECTED, ch.getId(),
                "Disconnected channel " + ch.getDisplayName(),
                java.util.Map.of("displayName", ch.getDisplayName()));
    }

    private Channel requireOwned(UUID tenantId, UUID id) {
        Channel ch = channels.findById(id).orElseThrow(() -> KonvoException.notFound("Channel", id));
        if (!ch.getTenantId().equals(tenantId)) {
            throw KonvoException.notFound("Channel", id);
        }
        return ch;
    }

    private ChannelResponse toResponse(Channel ch) {
        return toResponse(ch, false);
    }

    private ChannelResponse toResponse(Channel ch, boolean includeVerifyToken) {
        String webhookUrl = apiBaseUrl + "/api/webhooks/meta/" + ch.getId();
        return new ChannelResponse(
                ch.getId(),
                ch.getProvider(),
                ch.getDisplayName(),
                ch.getStatus(),
                ch.getPhoneNumber(),
                ch.getPhoneNumberId(),
                ch.getWabaId(),
                webhookUrl,
                includeVerifyToken ? ch.getWebhookVerifyToken() : null,
                ch.getCreatedAt());
    }

    private static String trimTrailingSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
