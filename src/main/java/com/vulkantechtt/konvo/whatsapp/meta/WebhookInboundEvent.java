package com.vulkantechtt.konvo.whatsapp.meta;

import java.util.UUID;

/**
 * Wire envelope for a verified inbound Meta webhook. We carry the raw bytes
 * (not a re-serialised JSON) so the listener parses the same shape Meta sent
 * — useful both for debugging and for any future re-verification.
 */
public record WebhookInboundEvent(UUID channelId, byte[] rawBody) {}
