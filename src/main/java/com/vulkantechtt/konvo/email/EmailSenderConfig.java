package com.vulkantechtt.konvo.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Picks an {@link EmailSender} based on {@code konvo.email.provider}.
 * {@code stub} (the default) writes the message body to the log so dev
 * password-reset / invitation links are still recoverable without SMTP.
 * {@code smtp} wires through Spring Mail's auto-configured
 * {@link JavaMailSender}; that bean only exists when
 * {@code spring.mail.host} is set, so an incomplete production config
 * fails fast instead of silently dropping mail.
 */
@Configuration
public class EmailSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderConfig.class);

    @Bean
    public EmailSender emailSender(
            @Value("${konvo.email.provider:stub}") String provider,
            @Value("${konvo.email.from:no-reply@konvo.tt}") String fromAddress,
            ObjectProvider<JavaMailSender> mailSender) {
        String key = provider == null ? "stub" : provider.toLowerCase();
        return switch (key) {
            case "smtp" -> {
                JavaMailSender resolved = mailSender.getIfAvailable();
                if (resolved == null) {
                    throw new IllegalStateException(
                            "konvo.email.provider=smtp but no JavaMailSender bean — set spring.mail.host etc.");
                }
                log.info("EmailSender = SMTP (from {})", fromAddress);
                yield new SmtpEmailSender(resolved, fromAddress);
            }
            default -> {
                log.info("EmailSender = stub (logs only; set konvo.email.provider=smtp for real send)");
                yield new StubEmailSender();
            }
        };
    }
}
