package com.vulkantechtt.konvo.ai;

import java.util.List;

/**
 * Chat-completion side of the AI layer. Replaces the M1 {@code AiProvider}
 * interface — the embedding side is split into {@link AiEmbeddingProvider}
 * because some providers (Groq) don't ship embeddings.
 *
 * Active implementation is chosen by {@code konvo.ai.default-provider} via
 * {@code @ConditionalOnProperty}: stub | openai | groq.
 */
public interface AiCompletionProvider {

    String name();

    Completion complete(CompletionRequest request);

    record CompletionRequest(
            String systemPrompt,
            List<Turn> turns,
            String userMessage,
            int maxTokens,
            double temperature) {

        public record Turn(Role role, String content) {}

        public enum Role { USER, ASSISTANT, SYSTEM }
    }

    record Completion(
            String text,
            int promptTokens,
            int completionTokens,
            String modelId,
            double costEstimateUsd) {}
}
