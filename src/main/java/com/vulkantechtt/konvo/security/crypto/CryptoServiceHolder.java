package com.vulkantechtt.konvo.security.crypto;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Bridge between Spring DI and the JPA {@link EncryptedStringConverter} (which
 * is instantiated by Hibernate, not Spring). Holds a single reference to the
 * {@link CryptoService} bean and exposes it statically.
 */
@Component
public class CryptoServiceHolder {

    private static volatile CryptoService instance;

    private final CryptoService crypto;

    public CryptoServiceHolder(CryptoService crypto) {
        this.crypto = crypto;
    }

    @PostConstruct
    void publish() {
        instance = crypto;
    }

    static CryptoService require() {
        CryptoService c = instance;
        if (c == null) {
            throw new IllegalStateException(
                    "CryptoService not initialised — EncryptedStringConverter "
                            + "was used before Spring context start-up completed");
        }
        return c;
    }
}
