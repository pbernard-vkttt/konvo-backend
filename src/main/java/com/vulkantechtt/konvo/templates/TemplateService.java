package com.vulkantechtt.konvo.templates;

import com.vulkantechtt.konvo.audit.AuditAction;
import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.channels.Channel;
import com.vulkantechtt.konvo.channels.ChannelRepository;
import com.vulkantechtt.konvo.common.AfterCommit;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.conversations.Conversation;
import com.vulkantechtt.konvo.conversations.ConversationRepository;
import com.vulkantechtt.konvo.conversations.Message;
import com.vulkantechtt.konvo.conversations.MessageDirection;
import com.vulkantechtt.konvo.conversations.MessageRepository;
import com.vulkantechtt.konvo.conversations.MessageStatus;
import com.vulkantechtt.konvo.customers.Customer;
import com.vulkantechtt.konvo.customers.CustomerRepository;
import com.vulkantechtt.konvo.realtime.SseHub;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.templates.dto.SendTemplateRequest;
import com.vulkantechtt.konvo.templates.dto.TemplateResponse;
import com.vulkantechtt.konvo.whatsapp.WhatsAppProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listing, syncing-from-Meta, and sending of {@link MessageTemplate}s.
 *
 * Sends go straight through the provider in the request transaction (not the
 * outbound queue) because templates are user-initiated and the user wants
 * immediate "did it work / did Meta reject it" feedback. Status updates from
 * Meta still flow back through the existing webhook → ingest listener.
 */
@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    private final MessageTemplateRepository templates;
    private final ChannelRepository channels;
    private final ConversationRepository conversations;
    private final CustomerRepository customers;
    private final MessageRepository messages;
    private final WhatsAppProvider provider;
    private final SseHub sseHub;
    private final AuditService audit;

    public TemplateService(
            MessageTemplateRepository templates,
            ChannelRepository channels,
            ConversationRepository conversations,
            CustomerRepository customers,
            MessageRepository messages,
            WhatsAppProvider provider,
            SseHub sseHub,
            AuditService audit) {
        this.templates = templates;
        this.channels = channels;
        this.conversations = conversations;
        this.customers = customers;
        this.messages = messages;
        this.provider = provider;
        this.sseHub = sseHub;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<TemplateResponse> list(KonvoPrincipal principal, Pageable pageable) {
        return PageResponse.from(templates
                .findByTenantIdOrderByNameAsc(principal.tenantId(), pageable)
                .map(TemplateService::toResponse));
    }

    /**
     * Re-syncs from Meta. Goes through every connected channel for this tenant,
     * calls the provider list endpoint, and upserts by (name, language).
     * Templates that disappear from Meta stay in the local table as
     * {@code disabled} for audit; a future "prune" action can hard-delete.
     */
    @Transactional
    public int syncFromMeta(KonvoPrincipal principal) {
        List<Channel> tenantChannels = channels.findByTenantId(principal.tenantId());
        if (tenantChannels.isEmpty()) {
            throw KonvoException.badRequest("Connect a WhatsApp channel before syncing templates");
        }
        int total = 0;
        for (Channel channel : tenantChannels) {
            List<WhatsAppProvider.TemplateSummary> remote = provider.listTemplates(channel.getId());
            for (WhatsAppProvider.TemplateSummary row : remote) {
                MessageTemplate t = templates
                        .findByTenantIdAndNameAndLanguage(principal.tenantId(), row.name(), row.language())
                        .orElseGet(MessageTemplate::new);
                if (t.getTenantId() == null) {
                    t.setTenantId(principal.tenantId());
                    t.setName(row.name());
                    t.setLanguage(row.language());
                }
                t.setCategory(parseCategory(row.category()));
                t.setStatus(parseStatus(row.status()));
                t.setMetaTemplateId(row.metaId());
                t.setComponents(row.componentsJson());
                templates.save(t);
                total++;
            }
        }
        log.info("Synced {} templates for tenant {}", total, principal.tenantId());
        audit.record(principal, AuditAction.TEMPLATE_SYNCED, null,
                "Synced " + total + " templates from Meta",
                java.util.Map.of("count", total, "channels", tenantChannels.size()));
        return total;
    }

    @Transactional
    public TemplateResponse send(KonvoPrincipal principal, UUID templateId, SendTemplateRequest req) {
        MessageTemplate template = templates.findById(templateId)
                .orElseThrow(() -> KonvoException.notFound("Template", templateId));
        if (!template.getTenantId().equals(principal.tenantId())) {
            throw KonvoException.notFound("Template", templateId);
        }
        if (template.getStatus() != TemplateStatus.approved) {
            throw KonvoException.badRequest("Only approved templates can be sent");
        }

        Resolved target = resolveTarget(principal, req);

        WhatsAppProvider.SendResult result = provider.sendTemplate(new WhatsAppProvider.SendTemplateCommand(
                target.channel.getId(),
                target.phone,
                template.getName(),
                req.language(),
                req.bodyParameters() == null ? List.of() : req.bodyParameters()));

        // Persist a message row for the outbound, even when there's no
        // conversation yet (greenfield templated outreach) — null conversation
        // is allowed by the schema and shows in the inbox once the customer
        // replies and a conversation gets opened from the ingest path.
        if (target.conversation != null) {
            Message msg = new Message();
            msg.setTenantId(principal.tenantId());
            msg.setConversationId(target.conversation.getId());
            msg.setDirection(MessageDirection.outbound);
            msg.setContentType("template");
            msg.setBody(renderPreview(template, req.bodyParameters()));
            msg.setWaMessageId(result.providerMessageId());
            msg.setStatus("sent".equals(result.status()) ? MessageStatus.sent : MessageStatus.queued);
            msg.setSentAt(Instant.now());
            messages.save(msg);

            target.conversation.setLastMessageAt(msg.getSentAt());
            target.conversation.setLastMessagePreview(msg.getBody());
            conversations.save(target.conversation);

            UUID tenantId = principal.tenantId();
            UUID conversationId = target.conversation.getId();
            UUID messageId = msg.getId();
            AfterCommit.run(() -> {
                sseHub.broadcast(tenantId, "message_appended",
                        java.util.Map.of("conversationId", conversationId.toString(),
                                "messageId", messageId.toString()));
                sseHub.broadcast(tenantId, "conversation_updated",
                        java.util.Map.of("conversationId", conversationId.toString()));
            });
        }
        audit.record(principal, AuditAction.TEMPLATE_SENT, template.getId(),
                "Sent template " + template.getName() + " to " + target.phone,
                java.util.Map.of("template", template.getName(),
                        "language", req.language(),
                        "to", target.phone));
        return toResponse(template);
    }

    // -- helpers -------------------------------------------------------------

    private Resolved resolveTarget(KonvoPrincipal principal, SendTemplateRequest req) {
        if (req.conversationId() != null) {
            Conversation conv = conversations.findById(req.conversationId())
                    .orElseThrow(() -> KonvoException.notFound("Conversation", req.conversationId()));
            if (!conv.getTenantId().equals(principal.tenantId())) {
                throw KonvoException.notFound("Conversation", req.conversationId());
            }
            Channel ch = channels.findById(conv.getChannelId())
                    .orElseThrow(() -> KonvoException.notFound("Channel", conv.getChannelId()));
            Customer cust = customers.findById(conv.getCustomerId())
                    .orElseThrow(() -> KonvoException.notFound("Customer", conv.getCustomerId()));
            return new Resolved(ch, cust.getPhone(), conv);
        }
        if (req.channelId() == null || req.toPhoneE164() == null || req.toPhoneE164().isBlank()) {
            throw KonvoException.badRequest("Either conversationId or (channelId + toPhoneE164) is required");
        }
        Channel ch = channels.findById(req.channelId())
                .orElseThrow(() -> KonvoException.notFound("Channel", req.channelId()));
        if (!ch.getTenantId().equals(principal.tenantId())) {
            throw KonvoException.notFound("Channel", req.channelId());
        }
        return new Resolved(ch, req.toPhoneE164(), null);
    }

    /** Best-effort "what the customer will see" for the inbox preview. We
     *  don't have the template body text here (Meta keeps it in components);
     *  for now we just stitch the parameters with the template name. */
    private static String renderPreview(MessageTemplate template, List<String> params) {
        if (params == null || params.isEmpty()) {
            return "(template: " + template.getName() + ")";
        }
        return "(template: " + template.getName() + ") " + String.join(" · ", params);
    }

    private static TemplateCategory parseCategory(String s) {
        if (s == null) return TemplateCategory.utility;
        return switch (s.toUpperCase()) {
            case "MARKETING" -> TemplateCategory.marketing;
            case "AUTHENTICATION" -> TemplateCategory.authentication;
            default -> TemplateCategory.utility;
        };
    }

    private static TemplateStatus parseStatus(String s) {
        if (s == null) return TemplateStatus.pending;
        return switch (s.toUpperCase()) {
            case "APPROVED" -> TemplateStatus.approved;
            case "REJECTED" -> TemplateStatus.rejected;
            case "PAUSED" -> TemplateStatus.paused;
            case "DISABLED" -> TemplateStatus.disabled;
            default -> TemplateStatus.pending;
        };
    }

    static TemplateResponse toResponse(MessageTemplate t) {
        return new TemplateResponse(
                t.getId(),
                t.getName(),
                t.getLanguage(),
                t.getCategory(),
                t.getStatus(),
                t.getComponents(),
                t.getUpdatedAt());
    }

    private record Resolved(Channel channel, String phone, Conversation conversation) {}
}
