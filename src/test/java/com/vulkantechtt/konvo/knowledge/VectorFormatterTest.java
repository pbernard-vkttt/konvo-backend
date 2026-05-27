package com.vulkantechtt.konvo.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VectorFormatterTest {

    @Test
    void wrapsValuesInBrackets() {
        assertThat(VectorFormatter.toLiteral(new float[]{1.0f, 2.0f, 3.0f}))
                .isEqualTo("[1.0,2.0,3.0]");
    }

    @Test
    void emptyArrayIsBracketsOnly() {
        assertThat(VectorFormatter.toLiteral(new float[]{})).isEqualTo("[]");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> VectorFormatter.toLiteral(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesNegativeAndFractional() {
        String out = VectorFormatter.toLiteral(new float[]{-0.5f, 0.25f, 0.0f});
        assertThat(out).isEqualTo("[-0.5,0.25,0.0]");
    }
}
