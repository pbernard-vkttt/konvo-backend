package com.vulkantechtt.konvo.email;

/**
 * Outbound transactional email. Implementations come from
 * {@link EmailSenderConfig} based on {@code konvo.email.provider}.
 * When {@code htmlBody} is non-null the message is sent as HTML/MIME;
 * otherwise a plain-text message is sent.
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
            String textBody,
            String htmlBody) {

        public static EmailMessage html(String toAddress, String toName, String subject, String htmlBody) {
            return new EmailMessage(toAddress, toName, subject, null, htmlBody);
        }

        public static EmailMessage plain(String toAddress, String toName, String subject, String textBody) {
            return new EmailMessage(toAddress, toName, subject, textBody, null);
        }
    }
}
