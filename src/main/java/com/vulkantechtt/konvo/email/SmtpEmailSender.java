package com.vulkantechtt.konvo.email;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Production email path. Uses Spring Mail's {@link JavaMailSender} (Lettuce-
 * style — configured via {@code spring.mail.*} properties). Provider-agnostic
 * by design; SendGrid / SES / Postmark all expose SMTP credentials.
 */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public String send(EmailMessage message) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(message.toAddress());
        msg.setSubject(message.subject());
        msg.setText(message.body());
        try {
            mailSender.send(msg);
        } catch (org.springframework.mail.MailException e) {
            log.error("SMTP send failed to {}: {}", message.toAddress(), e.getMessage());
            throw e;
        }
        // SimpleMailMessage doesn't expose the SMTP-server-assigned id; mint
        // a local one so the audit row has something to thread on.
        return "smtp-" + UUID.randomUUID();
    }

    @Override
    public String name() { return "smtp"; }
}
