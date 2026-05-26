package com.vulkantechtt.konvo.whatsapp.meta;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MetaSignatureVerifierTest {

    private static final String SECRET = "test-app-secret";
    private static final byte[] BODY = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsCorrectlySignedPayload() {
        String header = "sha256=" + MetaSignatureVerifier.computeHex(SECRET, BODY);
        assertThat(MetaSignatureVerifier.verify(SECRET, BODY, header)).isTrue();
    }

    @Test
    void rejectsMismatchedSignature() {
        String tampered = "sha256=" + MetaSignatureVerifier.computeHex("other-secret", BODY);
        assertThat(MetaSignatureVerifier.verify(SECRET, BODY, tampered)).isFalse();
    }

    @Test
    void rejectsWhenBodyMutated() {
        String header = "sha256=" + MetaSignatureVerifier.computeHex(SECRET, BODY);
        byte[] tampered = "{\"object\":\"whatsapp\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(MetaSignatureVerifier.verify(SECRET, tampered, header)).isFalse();
    }

    @Test
    void rejectsMissingPrefix() {
        String header = MetaSignatureVerifier.computeHex(SECRET, BODY); // no sha256= prefix
        assertThat(MetaSignatureVerifier.verify(SECRET, BODY, header)).isFalse();
    }

    @Test
    void rejectsNullInputs() {
        assertThat(MetaSignatureVerifier.verify(null, BODY, "sha256=abc")).isFalse();
        assertThat(MetaSignatureVerifier.verify(SECRET, null, "sha256=abc")).isFalse();
        assertThat(MetaSignatureVerifier.verify(SECRET, BODY, null)).isFalse();
    }

    @Test
    void rejectsEmptySecret() {
        assertThat(MetaSignatureVerifier.verify("", BODY, "sha256=abc")).isFalse();
    }
}
