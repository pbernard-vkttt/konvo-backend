package com.vulkantechtt.konvo.ai.groq;

import com.vulkantechtt.konvo.ai.AiCompletionProvider;
import com.vulkantechtt.konvo.ai.AiEmbeddingProvider;
import com.vulkantechtt.konvo.ai.StubAiProvider;
import com.vulkantechtt.konvo.common.KonvoException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Real Groq chat adapter. Active when {@code konvo.ai.default-provider=groq}.
 * Groq uses an OpenAI-compatible API, just a different base URL and model id.
 *
 * Groq doesn't ship embeddings — when this provider is active we register a
 * deterministic {@link StubAiProvider} as the embedding bean so the knowledge
 * indexer / retriever still works. Swap to OpenAI embeddings if you need
 * real vector search alongside Groq chat (a follow-up.)
 */
@Configuration
@ConditionalOnProperty(prefix = "konvo.ai", name = "default-provider", havingValue = "groq")
@EnableConfigurationProperties(GroqProperties.class)
public class GroqCompletionProvider {

    @Bean
    public AiCompletionProvider groqChatProvider(GroqProperties props) {
        return new Impl(props);
    }

    /** Embedding fallback — see class javadoc. */
    @Bean
    public AiEmbeddingProvider groqEmbeddingFallback() {
        return new StubAiProvider();
    }

    public static final class Impl implements AiCompletionProvider {

        private static final Logger log = LoggerFactory.getLogger(Impl.class);

        // Rough Llama 3.1 8B Instant Groq pricing — flat, conservative.
        private static final double USD_PER_PROMPT_TOKEN     = 0.05 / 1_000_000;
        private static final double USD_PER_COMPLETION_TOKEN = 0.08 / 1_000_000;

        private final GroqProperties props;
        private final RestClient http;

        Impl(GroqProperties props) {
            if (props.getApiKey() == null || props.getApiKey().isBlank()) {
                throw new IllegalStateException(
                        "konvo.ai.groq.api-key is required when konvo.ai.default-provider=groq");
            }
            this.props = props;
            this.http = RestClient.builder().baseUrl(props.getBaseUrl()).build();
        }

        @Override
        public String name() {
            return "groq";
        }

        @Override
        public Completion complete(CompletionRequest request) {
            List<Map<String, Object>> messages = new ArrayList<>();
            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                messages.add(Map.of("role", "system", "content", request.systemPrompt()));
            }
            if (request.turns() != null) {
                for (CompletionRequest.Turn t : request.turns()) {
                    messages.add(Map.of(
                            "role", switch (t.role()) {
                                case USER -> "user";
                                case ASSISTANT -> "assistant";
                                case SYSTEM -> "system";
                            },
                            "content", t.content()));
                }
            }
            if (request.userMessage() != null) {
                messages.add(Map.of("role", "user", "content", request.userMessage()));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", props.getChatModel());
            body.put("messages", messages);
            if (request.maxTokens() > 0) body.put("max_tokens", request.maxTokens());
            body.put("temperature", request.temperature());

            try {
                ChatResponse resp = http.post()
                        .uri("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(ChatResponse.class);
                if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
                    throw new KonvoException(HttpStatus.BAD_GATEWAY, "ai_empty_response",
                            "Groq returned no completion");
                }
                String text = resp.choices().get(0).message().content();
                int pt = resp.usage() != null ? resp.usage().prompt_tokens() : 0;
                int ct = resp.usage() != null ? resp.usage().completion_tokens() : 0;
                double cost = pt * USD_PER_PROMPT_TOKEN + ct * USD_PER_COMPLETION_TOKEN;
                return new Completion(text == null ? "" : text, pt, ct, props.getChatModel(), cost);
            } catch (RestClientResponseException e) {
                log.warn("Groq chat failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new KonvoException(HttpStatus.BAD_GATEWAY, "ai_chat_failed",
                        "AI completion failed: " + e.getStatusText());
            }
        }

        record ChatResponse(List<Choice> choices, Usage usage) {
            record Choice(Msg message) {}
            record Msg(String role, String content) {}
            record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {}
        }
    }
}
