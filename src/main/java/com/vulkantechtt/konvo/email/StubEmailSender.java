package com.vulkantechtt.konvo.email;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs message metadata and returns a synthetic message id. Dev profiles can
 * still expose one-shot links via API responses; logs should not preserve
 * reset or invitation tokens.
 */
public class StubEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(StubEmailSender.class);

    @Override
    public String send(EmailMessage message) {
        String id = "stub-" + UUID.randomUUID();
        log.info("EMAIL (stub provider) to: {} <{}> | subject: {} | body: <<redacted>>",
                message.toName(), message.toAddress(), message.subject());
        return id;
    }

    @Override
    public String name() { return "stub"; }
}
