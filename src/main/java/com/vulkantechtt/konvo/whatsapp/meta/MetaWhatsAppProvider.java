package com.vulkantechtt.konvo.whatsapp.meta;

import com.vulkantechtt.konvo.channels.Channel;
import com.vulkantechtt.konvo.channels.ChannelRepository;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.whatsapp.TransientSendException;
import com.vulkantechtt.konvo.whatsapp.WhatsAppProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Real Meta Cloud API adapter. Active when {@code konvo.whatsapp.provider=meta}.
 * Per-channel credentials live on the {@link Channel} row; globally we only
 * configure the Graph base URL and the pinned API version.
 *
 * Send shape (Graph v21):
 *   POST {graph}/v21.0/{phone_number_id}/messages
 *   Authorization: Bearer {access_token}
 *   { "messaging_product":"whatsapp", "to":"<E164>", "type":"text", "text":{"body":"..."} }
 *
 * Webhook verification: HMAC-SHA256 of the raw body using the channel's
 * app_secret, compared against the {@code X-Hub-Signature-256} header.
 */
@Component
@ConditionalOnProperty(prefix = "konvo.whatsapp", name = "provider", havingValue = "meta")
@EnableConfigurationProperties(MetaProperties.class)
public class MetaWhatsAppProvider implements WhatsAppProvider {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppProvider.class);

    private final MetaProperties props;
    private final ChannelRepository channels;
    private final RestClient http;
    private final ObjectMapper json;

    public MetaWhatsAppProvider(MetaProperties props, ChannelRepository channels, ObjectMapper json) {
        this.props = props;
        this.channels = channels;
        this.http = RestClient.builder().baseUrl(props.getGraphBaseUrl()).build();
        this.json = json;
    }

    @Override
    public String name() {
        return "meta";
    }

    @Override
    public SendResult sendText(SendTextCommand cmd) {
        Channel ch = channels.findById(cmd.channelId())
                .orElseThrow(() -> KonvoException.notFound("Channel", cmd.channelId()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", cmd.toPhoneE164());
        body.put("type", "text");
        body.put("text", Map.of("preview_url", false, "body", cmd.body()));

        String path = "/" + props.getGraphApiVersion() + "/" + ch.getPhoneNumberId() + "/messages";
        try {
            MetaSendResponse resp = http.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ch.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MetaSendResponse.class);

            String waMessageId = resp != null && resp.messages() != null && !resp.messages().isEmpty()
                    ? resp.messages().get(0).id()
                    : null;
            if (waMessageId == null) {
                log.warn("Meta send returned no wamid for channel={} response={}", cmd.channelId(), resp);
                return new SendResult("unknown", "queued");
            }
            return new SendResult(waMessageId, "sent");
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("Meta send failed channel={} status={} body={}",
                    cmd.channelId(), status, e.getResponseBodyAsString());
            if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                throw KonvoException.forbidden("WhatsApp credentials were rejected by Meta");
            }
            if (isRetryable(status)) {
                // 429 / 5xx — Meta is rate-limiting or having a wobble; let the
                // listener retry with backoff before giving up (audit H-2).
                throw new TransientSendException(
                        "WhatsApp send transient failure (HTTP " + status + ")", e);
            }
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "whatsapp_send_failed",
                    "WhatsApp send failed: " + e.getMessage());
        } catch (RestClientException e) {
            // No HTTP response at all — connection refused, timeout, DNS, etc.
            // Always worth retrying.
            log.warn("Meta send transport error channel={}: {}", cmd.channelId(), e.toString());
            throw new TransientSendException("WhatsApp send transport error", e);
        }
    }

    private static boolean isRetryable(int status) {
        return status == HttpStatus.TOO_MANY_REQUESTS.value() || status >= 500;
    }

    @Override
    public SendResult sendTemplate(SendTemplateCommand cmd) {
        Channel ch = channels.findById(cmd.channelId())
                .orElseThrow(() -> KonvoException.notFound("Channel", cmd.channelId()));

        // Build the template payload. Body parameters are positional in Meta
        // (placeholder {{1}} = first item). Header / button parameters are
        // out of scope for M6 — easy follow-up when those template shapes
        // start appearing.
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", cmd.templateName());
        template.put("language", Map.of("code", cmd.language()));
        List<Map<String, Object>> components = new ArrayList<>();
        if (cmd.bodyParameters() != null && !cmd.bodyParameters().isEmpty()) {
            List<Map<String, Object>> params = new ArrayList<>(cmd.bodyParameters().size());
            for (String p : cmd.bodyParameters()) {
                params.add(Map.of("type", "text", "text", p));
            }
            components.add(Map.of("type", "body", "parameters", params));
        }
        if (!components.isEmpty()) {
            template.put("components", components);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", cmd.toPhoneE164());
        body.put("type", "template");
        body.put("template", template);

        String path = "/" + props.getGraphApiVersion() + "/" + ch.getPhoneNumberId() + "/messages";
        try {
            MetaSendResponse resp = http.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ch.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MetaSendResponse.class);
            String waMessageId = resp != null && resp.messages() != null && !resp.messages().isEmpty()
                    ? resp.messages().get(0).id()
                    : null;
            if (waMessageId == null) {
                log.warn("Meta send-template returned no wamid channel={}", cmd.channelId());
                return new SendResult("unknown", "queued");
            }
            return new SendResult(waMessageId, "sent");
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("Meta send-template failed channel={} status={} body={}",
                    cmd.channelId(), status, e.getResponseBodyAsString());
            if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                throw KonvoException.forbidden("WhatsApp credentials were rejected by Meta");
            }
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "whatsapp_send_failed",
                    "WhatsApp template send failed: " + e.getMessage());
        }
    }

    @Override
    public List<TemplateSummary> listTemplates(UUID channelId) {
        Channel ch = channels.findById(channelId)
                .orElseThrow(() -> KonvoException.notFound("Channel", channelId));
        if (ch.getWabaId() == null || ch.getWabaId().isBlank()) {
            throw KonvoException.badRequest("This channel has no WhatsApp Business Account id");
        }
        String path = "/" + props.getGraphApiVersion() + "/" + ch.getWabaId()
                + "/message_templates?limit=100";
        try {
            MetaTemplateListResponse resp = http.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ch.getAccessToken())
                    .retrieve()
                    .body(MetaTemplateListResponse.class);
            if (resp == null || resp.data() == null) return List.of();
            List<TemplateSummary> out = new ArrayList<>(resp.data().size());
            for (MetaTemplateListResponse.Row row : resp.data()) {
                String componentsJson = null;
                if (row.components() != null) {
                    try {
                        // Round-trip through Jackson so we store canonical JSON.
                        ArrayNode arr = json.valueToTree(row.components());
                        componentsJson = arr.toString();
                    } catch (RuntimeException e) {
                        log.debug("Could not serialise template components for {}: {}",
                                row.name(), e.toString());
                    }
                }
                out.add(new TemplateSummary(
                        row.id(),
                        row.name(),
                        row.language(),
                        row.category(),
                        row.status(),
                        componentsJson));
            }
            return out;
        } catch (RestClientResponseException e) {
            log.warn("Meta template list failed channel={} status={} body={}",
                    channelId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "templates_sync_failed",
                    "Template sync failed: " + e.getMessage());
        }
    }

    @Override
    public boolean verifyWebhookSignature(UUID channelId, byte[] rawBody, String signatureHeader) {
        Channel ch = channels.findById(channelId).orElse(null);
        if (ch == null || ch.getAppSecret() == null || ch.getAppSecret().isBlank()) {
            return false;
        }
        return MetaSignatureVerifier.verify(ch.getAppSecret(), rawBody, signatureHeader);
    }

    /** Subset of the Meta send response — we only care about the wamid echo. */
    record MetaSendResponse(List<Msg> messages) {
        record Msg(String id) {}
    }

    /** Subset of {@code GET /{waba}/message_templates}. */
    record MetaTemplateListResponse(List<Row> data) {
        record Row(String id, String name, String language, String category, String status,
                   List<Map<String, Object>> components) {}
    }
}
