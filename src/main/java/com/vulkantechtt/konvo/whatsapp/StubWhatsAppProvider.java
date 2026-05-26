package com.vulkantechtt.konvo.whatsapp;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-network stub. Active when konvo.whatsapp.provider=stub (the M1 default).
 * Lets the rest of the stack — webhook ingestion, inbox, AI replies — run
 * end-to-end without Meta credentials.
 */
@Component
@ConditionalOnProperty(prefix = "konvo.whatsapp", name = "provider", havingValue = "stub", matchIfMissing = true)
public class StubWhatsAppProvider implements WhatsAppProvider {

    private static final Logger log = LoggerFactory.getLogger(StubWhatsAppProvider.class);

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public SendResult sendText(SendTextCommand cmd) {
        String id = "stub-msg-" + UUID.randomUUID();
        log.info("[stub-whatsapp] send channel={} to={} bodyLen={} -> id={}",
                cmd.channelId(), cmd.toPhoneE164(),
                cmd.body() == null ? 0 : cmd.body().length(), id);
        return new SendResult(id, "queued");
    }

    @Override
    public boolean verifyWebhookSignature(String body, String signatureHeader) {
        // The stub accepts anything; the Meta adapter (M3) will enforce HMAC-SHA256.
        return true;
    }
}
