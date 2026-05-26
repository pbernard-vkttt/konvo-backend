package com.vulkantechtt.konvo.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

    @Test
    void hashIsDeterministic() {
        String t = "konvo-test-token";
        assertThat(TokenHasher.hash(t)).isEqualTo(TokenHasher.hash(t));
    }

    @Test
    void hashIsDifferentForDifferentInputs() {
        assertThat(TokenHasher.hash("a")).isNotEqualTo(TokenHasher.hash("b"));
    }

    @Test
    void hashIsHex64() {
        String h = TokenHasher.hash("anything");
        assertThat(h).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void randomTokenLooksUrlSafe() {
        String t = TokenHasher.randomToken();
        assertThat(t).matches("[A-Za-z0-9_-]+");
        assertThat(TokenHasher.randomToken()).isNotEqualTo(t);
    }
}
