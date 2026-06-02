package com.vulkantechtt.konvo.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CryptoServiceTest {

    private static final String DEV_KEY =
            Base64.getEncoder().encodeToString("test-only-32-byte-aes-key-padded".getBytes());

    @Test
    void roundTripsPlaintext() {
        CryptoService crypto = new CryptoService(DEV_KEY);
        String pt = "EAAGtest-meta-access-token-abc123";
        String ct = crypto.encrypt(pt);
        assertThat(ct).startsWith(CryptoService.PREFIX);
        assertThat(crypto.decrypt(ct)).isEqualTo(pt);
    }

    @Test
    void encryptingSamePlaintextTwiceProducesDifferentCiphertext() {
        CryptoService crypto = new CryptoService(DEV_KEY);
        String pt = "secret-value";
        assertThat(crypto.encrypt(pt)).isNotEqualTo(crypto.encrypt(pt));
    }

    @Test
    void decryptingLegacyPlaintextPassesThrough() {
        // Pre-M8 channels stored raw secrets in the column. On read we want
        // them to keep working, then be re-encrypted on next write.
        CryptoService crypto = new CryptoService(DEV_KEY);
        assertThat(crypto.decrypt("legacy-app-secret-plaintext")).isEqualTo("legacy-app-secret-plaintext");
    }

    @Test
    void nullsRoundTripAsNull() {
        CryptoService crypto = new CryptoService(DEV_KEY);
        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.decrypt(null)).isNull();
    }

    @Test
    void rejectsKeyOfWrongLength() {
        String tooShort = Base64.getEncoder().encodeToString("only-16-byte-key".getBytes());
        assertThatThrownBy(() -> new CryptoService(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void rejectsTamperedCiphertext() {
        CryptoService crypto = new CryptoService(DEV_KEY);
        String ct = crypto.encrypt("payload");
        String tampered = ct.substring(0, ct.length() - 4) + "AAAA";
        assertThatThrownBy(() -> crypto.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Decryption failed");
    }
}
