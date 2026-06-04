package com.vulkantechtt.konvo.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Production email path. Uses Spring Mail's {@link JavaMailSender}.
 * HTML messages are sent via {@link MimeMessageHelper}; plain-text messages
 * use {@link SimpleMailMessage} (lighter-weight).
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
        try {
            if (message.htmlBody() != null) {
                sendHtml(message);
            } else {
                sendPlain(message);
            }
        } catch (org.springframework.mail.MailException e) {
            log.error("SMTP send failed to {}: {}", message.toAddress(), e.getMessage());
            throw e;
        } catch (MessagingException e) {
            log.error("MIME build failed for {}: {}", message.toAddress(), e.getMessage());
            throw new org.springframework.mail.MailSendException("MIME build failed", e);
        }
        return "smtp-" + UUID.randomUUID();
    }

    private void sendPlain(EmailMessage message) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(message.toAddress());
        msg.setSubject(message.subject());
        msg.setText(message.textBody());
        mailSender.send(msg);
    }

    private void sendHtml(EmailMessage message) throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(message.toAddress());
        helper.setSubject(message.subject());
        helper.setText(message.htmlBody(), true);
        mailSender.send(mime);
    }

    @Override
    public String name() { return "smtp"; }
}
