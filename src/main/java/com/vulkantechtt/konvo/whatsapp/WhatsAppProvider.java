package com.vulkantechtt.konvo.whatsapp;

import java.util.UUID;

/**
 * Provider-neutral abstraction for the WhatsApp channel.
 *
 * MVP only implements Meta Cloud API directly, but the interface lets a BSP
 * (Twilio, 360dialog, etc.) drop in later without disturbing the inbox or AI
 * code paths. The plan calls this out explicitly in §1 (WhatsApp).
 */
public interface WhatsAppProvider {

    String name();

    SendResult sendText(SendTextCommand cmd);

    /**
     * HMAC-verify a webhook body against the channel's app secret. The
     * channelId comes from the webhook URL path (the controller routes to
     * {@code /api/webhooks/meta/{channelId}}). The raw byte body is passed
     * (not a decoded string) because Meta's signature is computed over the
     * exact bytes — a re-encode would shift the digest.
     */
    boolean verifyWebhookSignature(UUID channelId, byte[] rawBody, String signatureHeader);

    record SendTextCommand(
            UUID channelId,
            String toPhoneE164,
            String body,
            String replyToProviderMessageId) {}

    record SendResult(String providerMessageId, String status) {}
}
