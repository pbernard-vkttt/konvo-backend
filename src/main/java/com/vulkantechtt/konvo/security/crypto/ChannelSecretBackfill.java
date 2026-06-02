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
        var rows = jdbc.queryForList("""
                select id, app_secret, access_token
                from channels
                where (app_secret is not null and app_secret not like ?)
                   or (access_token is not null and access_token not like ?)
                """, CryptoService.PREFIX + "%", CryptoService.PREFIX + "%");

        int touched = 0;
        for (var row : rows) {
            UUID id = (UUID) row.get("id");
            String appSecret = (String) row.get("app_secret");
            String accessToken = (String) row.get("access_token");
            String nextAppSecret = encryptIfNeeded(appSecret);
            String nextAccessToken = encryptIfNeeded(accessToken);
            touched += jdbc.update("""
                    update channels
                    set app_secret = ?, access_token = ?, updated_at = now()
                    where id = ?
                    """, nextAppSecret, nextAccessToken, id);
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
