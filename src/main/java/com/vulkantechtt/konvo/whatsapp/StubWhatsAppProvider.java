package com.vulkantechtt.konvo.whatsapp;

import java.util.List;
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
    public SendResult sendTemplate(SendTemplateCommand cmd) {
        String id = "stub-tpl-" + UUID.randomUUID();
        log.info("[stub-whatsapp] send-template channel={} to={} template={} lang={} params={} -> id={}",
                cmd.channelId(), cmd.toPhoneE164(), cmd.templateName(),
                cmd.language(),
                cmd.bodyParameters() == null ? 0 : cmd.bodyParameters().size(), id);
        return new SendResult(id, "queued");
    }

    @Override
    public List<TemplateSummary> listTemplates(UUID channelId) {
        // No real Meta connection; templates are sync-from-Meta only.
        return List.of();
    }

    @Override
    public boolean verifyWebhookSignature(UUID channelId, byte[] rawBody, String signatureHeader) {
        // The stub accepts anything; MetaWhatsAppProvider enforces HMAC-SHA256.
        return true;
    }
}
