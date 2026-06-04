package com.vulkantechtt.konvo.security.crypto;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "konvo.security.backfill-channel-secrets",
        havingValue = "true",
        matchIfMissing = true)
public class ChannelSecretBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChannelSecretBackfill.class);

    private final JdbcTemplate jdbc;
    private final CryptoService crypto;

    public ChannelSecretBackfill(JdbcTemplate jdbc, CryptoService crypto) {
        this.jdbc = jdbc;
        this.crypto = crypto;
    }

    @Override
    public void run(ApplicationArguments args) {
        String notEncrypted = CryptoService.PREFIX + "%";
        var rows = jdbc.queryForList("""
                select id, app_secret, access_token, webhook_verify_token
                from channels
                where (app_secret is not null and app_secret not like ?)
                   or (access_token is not null and access_token not like ?)
                   or (webhook_verify_token is not null and webhook_verify_token not like ?)
                """, notEncrypted, notEncrypted, notEncrypted);

        int touched = 0;
        for (var row : rows) {
            UUID id = (UUID) row.get("id");
            String nextAppSecret = encryptIfNeeded((String) row.get("app_secret"));
            String nextAccessToken = encryptIfNeeded((String) row.get("access_token"));
            String nextVerifyToken = encryptIfNeeded((String) row.get("webhook_verify_token"));
            touched += jdbc.update("""
                    update channels
                    set app_secret = ?, access_token = ?, webhook_verify_token = ?, updated_at = now()
                    where id = ?
                    """, nextAppSecret, nextAccessToken, nextVerifyToken, id);
        }
        if (touched > 0) {
            log.info("Encrypted {} legacy channel secret row(s)", touched);
        }
    }

    private String encryptIfNeeded(String value) {
        if (value == null || value.startsWith(CryptoService.PREFIX)) {
            return value;
        }
        return crypto.encrypt(value);
    }
}
