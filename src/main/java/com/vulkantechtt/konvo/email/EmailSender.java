package com.vulkantechtt.konvo.email;

/**
 * Outbound transactional email. Implementations come from
 * {@link EmailSenderConfig} based on {@code konvo.email.provider}. Plaintext
 * body only for M8 — HTML templates land when we have a marketing surface
 * with reusable layouts; the M8 messages are functional (password reset
 * link, invite link, "Vee paused" digest) and look fine as text.
 */
public interface EmailSender {

    /** Send and return a provider-side message id (or a synthetic one for the stub). */
    String send(EmailMessage message);

    /** A short identifier for logs and audit rows. */
    String name();

    record EmailMessage(
            String toAddress,
            String toName,
            String subject,
            String body) {
    }
}
