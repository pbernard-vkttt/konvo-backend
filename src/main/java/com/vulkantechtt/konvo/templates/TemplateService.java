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
import com.vulkantechtt.konvo.templates.dto.CreateTemplateRequest;
import com.vulkantechtt.konvo.templates.dto.SendTemplateRequest;
import com.vulkantechtt.konvo.templates.dto.TemplateResponse;
import com.vulkantechtt.konvo.whatsapp.WhatsAppProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

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
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\d+)}}");

    private final MessageTemplateRepository templates;
    private final ChannelRepository channels;
    private final ConversationRepository conversations;
    private final CustomerRepository customers;
    private final MessageRepository messages;
    private final WhatsAppProvider provider;
    private final SseHub sseHub;
    private final AuditService audit;
    private final ObjectMapper json;

    public TemplateService(
            MessageTemplateRepository templates,
            ChannelRepository channels,
            ConversationRepository conversations,
            CustomerRepository customers,
            MessageRepository messages,
            WhatsAppProvider provider,
            SseHub sseHub,
            AuditService audit,
            ObjectMapper json) {
        this.templates = templates;
        this.channels = channels;
        this.conversations = conversations;
        this.customers = customers;
        this.messages = messages;
        this.provider = provider;
        this.sseHub = sseHub;
        this.audit = audit;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public PageResponse<TemplateResponse> list(KonvoPrincipal principal, Pageable pageable) {
        return PageResponse.from(templates
                .findByTenantIdOrderByNameAsc(principal.tenantId(), pageable)
                .map(TemplateService::toResponse));
    }

    @Transactional
    public TemplateResponse create(KonvoPrincipal principal, CreateTemplateRequest req) {
        String name = req.name().trim();
        String language = req.language().trim();
        if (templates.findByTenantIdAndNameAndLanguage(principal.tenantId(), name, language).isPresent()) {
            throw KonvoException.badRequest("A template with this name and language already exists. Sync from Meta if it was created elsewhere.");
        }

        Channel channel = connectedWhatsAppChannel(principal.tenantId());
        List<Map<String, Object>> components = buildTextComponents(req);
        String componentsJson = toCanonicalJson(components);

        WhatsAppProvider.CreateTemplateResult result = provider.createTemplate(new WhatsAppProvider.CreateTemplateCommand(
                channel.getId(),
                name,
                language,
                req.category().name().toUpperCase(),
                components));

        MessageTemplate saved = upsertTemplate(
                principal.tenantId(),
                name,
                language,
                req.category(),
                parseStatus(result.status()),
                result.metaTemplateId(),
                componentsJson);

        audit.record(principal, AuditAction.TEMPLATE_CREATED, saved.getId(),
                "Created template " + name + " and submitted it to Meta for approval",
                Map.of("name", name, "language", language, "category", req.category().name()));
        return toResponse(saved);
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
                upsertTemplate(
                        principal.tenantId(),
                        row.name(),
                        row.language(),
                        parseCategory(row.category()),
                        parseStatus(row.status()),
                        row.metaId(),
                        row.componentsJson());
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
                "Sent template " + template.getName() + " to " + redactPhone(target.phone),
                java.util.Map.of("template", template.getName(),
                        "language", req.language(),
                        "to", redactPhone(target.phone)));
        return toResponse(template);
    }

    // -- helpers -------------------------------------------------------------

    private Channel connectedWhatsAppChannel(UUID tenantId) {
        return channels.findByTenantId(tenantId).stream()
                .filter(ch -> ch.getProvider() != null && ch.getProvider().name().startsWith("whatsapp"))
                .findFirst()
                .orElseThrow(() -> KonvoException.badRequest("Connect a WhatsApp channel before creating templates"));
    }

    private MessageTemplate upsertTemplate(
            UUID tenantId,
            String name,
            String language,
            TemplateCategory category,
            TemplateStatus status,
            String metaTemplateId,
            String componentsJson) {
        MessageTemplate t = templates
                .findByTenantIdAndNameAndLanguage(tenantId, name, language)
                .orElseGet(MessageTemplate::new);
        if (t.getTenantId() == null) {
            t.setTenantId(tenantId);
            t.setName(name);
            t.setLanguage(language);
        }
        t.setCategory(category);
        t.setStatus(status);
        t.setMetaTemplateId(metaTemplateId);
        t.setComponents(componentsJson);
        return templates.save(t);
    }

    private List<Map<String, Object>> buildTextComponents(CreateTemplateRequest req) {
        List<Map<String, Object>> components = new ArrayList<>();

        String headerText = trimToNull(req.headerText());
        List<String> headerExamples = sanitiseExamples(req.headerExamples());
        if (headerText != null) {
            LinkedHashMap<String, Object> header = new LinkedHashMap<>();
            header.put("type", "HEADER");
            header.put("format", "TEXT");
            header.put("text", headerText);
            int headerVars = countPlaceholders(headerText);
            validateExamples("Header examples", headerVars, headerExamples);
            if (headerVars > 0) {
                header.put("example", Map.of("header_text", headerExamples));
            }
            components.add(header);
        } else if (!headerExamples.isEmpty()) {
            throw KonvoException.badRequest("Header examples require header text");
        }

        String bodyText = req.bodyText().trim();
        List<String> bodyExamples = sanitiseExamples(req.bodyExamples());
        int bodyVars = countPlaceholders(bodyText);
        validateExamples("Body examples", bodyVars, bodyExamples);

        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("type", "BODY");
        body.put("text", bodyText);
        if (bodyVars > 0) {
            body.put("example", Map.of("body_text", List.of(bodyExamples)));
        }
        components.add(body);

        String footerText = trimToNull(req.footerText());
        if (footerText != null) {
            components.add(Map.of("type", "FOOTER", "text", footerText));
        }

        return components;
    }

    private static List<String> sanitiseExamples(List<String> examples) {
        if (examples == null || examples.isEmpty()) return List.of();
        return examples.stream()
                .map(TemplateService::trimToNull)
                .filter(v -> v != null)
                .toList();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void validateExamples(String label, int placeholders, List<String> examples) {
        if (placeholders == 0 && !examples.isEmpty()) {
            throw KonvoException.badRequest(label + " are only allowed when the text includes {{1}} placeholders");
        }
        if (placeholders > 0 && examples.size() != placeholders) {
            throw KonvoException.badRequest(label + " must include exactly " + placeholders + " sample value(s)");
        }
    }

    private static int countPlaceholders(String text) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        int expected = 0;
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index != expected + 1) {
                throw KonvoException.badRequest("Template placeholders must be sequential starting at {{1}}");
            }
            expected = index;
        }
        return expected;
    }

    private String toCanonicalJson(List<Map<String, Object>> components) {
        try {
            return json.valueToTree(components).toString();
        } catch (RuntimeException e) {
            throw new KonvoException(org.springframework.http.HttpStatus.BAD_REQUEST, "template_payload_invalid",
                    "Could not build the template payload");
        }
    }

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

    /** Redacts a phone number for audit logs: keeps country prefix (up to 5 chars) and last 4 digits. */
    static String redactPhone(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        int prefix = Math.min(5, phone.length() / 2);
        int suffix = Math.min(4, phone.length() - prefix - 1);
        return phone.substring(0, prefix) + "..." + phone.substring(phone.length() - suffix);
    }
}
