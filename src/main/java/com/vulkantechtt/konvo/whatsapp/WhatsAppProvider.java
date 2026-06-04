package com.vulkantechtt.konvo.whatsapp;

import java.util.List;
import java.util.UUID;

/**
 * Provider-neutral abstraction for the WhatsApp channel.
 *
 * MVP implements Meta Cloud API directly, but the interface lets a BSP
 * (Twilio, 360dialog, etc.) drop in later without disturbing the inbox or AI
 * code paths. The plan calls this out explicitly in §1 (WhatsApp).
 */
public interface WhatsAppProvider {

    String name();

    SendResult sendText(SendTextCommand cmd);

    /** Send a pre-approved Meta template. Used to reach a customer outside
     *  the 24-hour customer-service window. */
    SendResult sendTemplate(SendTemplateCommand cmd);

    /** Create a new template under the channel's WABA and submit it to Meta
     *  for review/approval. */
    CreateTemplateResult createTemplate(CreateTemplateCommand cmd);

    /** List the WhatsApp message templates Meta knows about for this
     *  channel's WABA. Stub returns an empty list — Meta is the only
     *  provider that can answer meaningfully. */
    List<TemplateSummary> listTemplates(UUID channelId);

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

    record SendTemplateCommand(
            UUID channelId,
            String toPhoneE164,
            String templateName,
            String language,
            List<String> bodyParameters) {}

    record CreateTemplateCommand(
            UUID channelId,
            String name,
            String language,
            String category,
            List<java.util.Map<String, Object>> components) {}

    record SendResult(String providerMessageId, String status) {}

    record CreateTemplateResult(String metaTemplateId, String status) {}

    /** What we surface from Meta's template list so the sync service can
     *  upsert without caring about JSON shape. */
    record TemplateSummary(
            String metaId,
            String name,
            String language,
            String category,
            String status,
            String componentsJson) {}
}
