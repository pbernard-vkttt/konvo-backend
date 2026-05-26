package com.vulkantechtt.konvo.whatsapp.meta;

import com.vulkantechtt.konvo.channels.Channel;
import com.vulkantechtt.konvo.channels.ChannelRepository;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.whatsapp.WhatsAppProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
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

    public MetaWhatsAppProvider(MetaProperties props, ChannelRepository channels) {
        this.props = props;
        this.channels = channels;
        this.http = RestClient.builder().baseUrl(props.getGraphBaseUrl()).build();
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
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "whatsapp_send_failed",
                    "WhatsApp send failed: " + e.getMessage());
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
}
