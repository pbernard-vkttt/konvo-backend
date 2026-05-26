package com.vulkantechtt.konvo.ai;

import java.util.List;

/**
 * Provider-neutral interface for the AI model layer.
 *
 * The plan calls for a router that picks a low-cost model by default and
 * escalates only when needed; that router (M5) sits in front of this
 * interface and chooses which AiProvider bean to call.
 */
public interface AiProvider {

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
