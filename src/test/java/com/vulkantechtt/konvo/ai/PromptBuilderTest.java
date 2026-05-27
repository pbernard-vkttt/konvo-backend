package com.vulkantechtt.konvo.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulkantechtt.konvo.knowledge.KnowledgeRetriever;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {

    @Test
    void includesWorkspaceNameAndTriniGuidance() {
        String prompt = PromptBuilder.systemPrompt("Doubles King", List.of());
        assertThat(prompt).contains("Doubles King");
        assertThat(prompt).contains("Trini");
        assertThat(prompt).contains("Never invent facts");
    }

    @Test
    void emptyHitsShowExplicitMarker() {
        String prompt = PromptBuilder.systemPrompt("Shop", List.of());
        assertThat(prompt).contains("(no knowledge base entries found for this query)");
    }

    @Test
    void includesAllHitsInOrder() {
        KnowledgeRetriever.Hit h1 = new KnowledgeRetriever.Hit(
                UUID.randomUUID(), UUID.randomUUID(), "Hours", 0,
                "We open 6am to 10pm.", 0.1);
        KnowledgeRetriever.Hit h2 = new KnowledgeRetriever.Hit(
                UUID.randomUUID(), UUID.randomUUID(), "Menu", 0,
                "TT$15 for doubles.", 0.2);

        String prompt = PromptBuilder.systemPrompt("Shop", List.of(h1, h2));

        assertThat(prompt).contains("[1] from \"Hours\"");
        assertThat(prompt).contains("We open 6am to 10pm.");
        assertThat(prompt).contains("[2] from \"Menu\"");
        assertThat(prompt).contains("TT$15 for doubles.");
        assertThat(prompt.indexOf("[1]")).isLessThan(prompt.indexOf("[2]"));
    }

    @Test
    void handlesNullWorkspaceName() {
        String prompt = PromptBuilder.systemPrompt(null, List.of());
        assertThat(prompt).contains("this workspace");
    }
}
