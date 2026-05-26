package com.vulkantechtt.konvo.whatsapp.meta;

import com.vulkantechtt.konvo.channels.Channel;
import com.vulkantechtt.konvo.channels.ChannelRepository;
import com.vulkantechtt.konvo.config.RabbitConfig;
import com.vulkantechtt.konvo.whatsapp.WhatsAppProvider;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Meta's webhook traffic for a single channel. Two methods:
 *
 *  * GET — the verification handshake. Meta sends
 *    {@code ?hub.mode=subscribe&hub.verify_token=...&hub.challenge=...} and
 *    expects the challenge back verbatim with 200. We compare against the
 *    per-channel {@code webhook_verify_token}.
 *
 *  * POST — actual events. We HMAC-verify the body against the channel's
 *    app_secret, then publish to AMQP and ACK 200 fast. Meta enforces a
 *    short response budget; doing the work synchronously risks redeliveries.
 *
 * The unauthenticated nature of the endpoint is intentional — Meta is the
 * caller and authenticates via signature. SecurityConfig permits
 * {@code /api/webhooks/**}.
 */
@RestController
@RequestMapping("/api/webhooks/meta/{channelId}")
public class MetaWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MetaWebhookController.class);

    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

    private final ChannelRepository channels;
    private final WhatsAppProvider provider;
    private final RabbitTemplate rabbit;

    public MetaWebhookController(ChannelRepository channels,
                                 WhatsAppProvider provider,
                                 RabbitTemplate rabbit) {
        this.channels = channels;
        this.provider = provider;
        this.rabbit = rabbit;
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @PathVariable UUID channelId,
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        Channel ch = channels.findById(channelId).orElse(null);
        if (ch == null || !"subscribe".equals(mode) || token == null
                || !token.equals(ch.getWebhookVerifyToken()) || challenge == null) {
            log.info("Meta webhook verify rejected channel={} mode={} tokenMatch={}",
                    channelId, mode, ch != null && token != null && token.equals(ch.getWebhookVerifyToken()));
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(challenge);
    }

    @PostMapping
    public ResponseEntity<Void> ingest(
            @PathVariable UUID channelId,
            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody byte[] rawBody) {

        if (!provider.verifyWebhookSignature(channelId, rawBody, signature)) {
            log.warn("Meta webhook signature rejected channel={}", channelId);
            // Meta retries on 4xx; return 401 so it's clear in their dashboard.
            return ResponseEntity.status(401).build();
        }

        try {
            rabbit.convertAndSend(
                    RabbitConfig.EVENTS_EXCHANGE,
                    "webhook.inbound.meta",
                    new WebhookInboundEvent(channelId, rawBody));
        } catch (Exception e) {
            // If publish fails we want Meta to retry, not lose the event.
            log.error("Failed to publish webhook to AMQP for channel={}", channelId, e);
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok().build();
    }
}
