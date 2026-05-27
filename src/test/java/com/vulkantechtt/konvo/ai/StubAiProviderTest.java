package com.vulkantechtt.konvo.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StubAiProviderTest {

    private final StubAiProvider stub = new StubAiProvider();

    @Test
    void deterministicEmbeddingMatchesDeclaredDims() {
        float[] v = stub.embedOne("doubles");
        assertThat(v).hasSize(stub.dimensions());
        assertThat(stub.dimensions()).isEqualTo(1536);
    }

    @Test
    void sameInputProducesSameEmbedding() {
        assertThat(stub.embedOne("hello")).isEqualTo(stub.embedOne("hello"));
    }

    @Test
    void differentInputsProduceDifferentEmbeddings() {
        assertThat(stub.embedOne("hello")).isNotEqualTo(stub.embedOne("world"));
    }

    @Test
    void valuesAreInRange() {
        float[] v = stub.embedOne("trinidad");
        for (float f : v) {
            assertThat(f).isBetween(-1.0f, 1.0f);
        }
    }

    @Test
    void chatCannedResponsePicksByKeyword() {
        var r = stub.complete(new AiCompletionProvider.CompletionRequest(
                null, List.of(), "What are your hours?", 100, 0.0));
        assertThat(r.text().toLowerCase()).contains("6 am");
    }
}
