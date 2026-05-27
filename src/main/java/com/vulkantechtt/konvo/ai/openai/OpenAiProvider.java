package com.vulkantechtt.konvo.ai.openai;

import com.vulkantechtt.konvo.ai.AiCompletionProvider;
import com.vulkantechtt.konvo.ai.AiEmbeddingProvider;
import com.vulkantechtt.konvo.common.KonvoException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Real OpenAI adapter. Active when {@code konvo.ai.default-provider=openai}.
 * Implements both completion and embedding.
 *
 * Pricing: rough per-token USD multipliers baked in for the audit row. They
 * drift; keep the numbers conservative and surface a "verify in OpenAI
 * dashboard" link in the Insights view when that lands.
 */
@Component
@ConditionalOnProperty(prefix = "konvo.ai", name = "default-provider", havingValue = "openai")
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiProvider implements AiCompletionProvider, AiEmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    private static final int EMBED_DIMS = 1536;

    // Conservative-ish defaults for gpt-4o-mini + text-embedding-3-small.
    private static final double CHAT_USD_PER_PROMPT_TOKEN     = 0.15  / 1_000_000;
    private static final double CHAT_USD_PER_COMPLETION_TOKEN = 0.60  / 1_000_000;
    private static final double EMBED_USD_PER_TOKEN           = 0.02  / 1_000_000;

    private final OpenAiProperties props;
    private final RestClient http;

    public OpenAiProvider(OpenAiProperties props) {
        this.props = props;
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "konvo.ai.openai.api-key is required when konvo.ai.default-provider=openai");
        }
        this.http = RestClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public int dimensions() {
        return EMBED_DIMS;
    }

    // -- chat ---------------------------------------------------------------

    @Override
    public Completion complete(CompletionRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        if (request.turns() != null) {
            for (CompletionRequest.Turn t : request.turns()) {
                messages.add(Map.of("role", roleOf(t.role()), "content", t.content()));
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
                        "OpenAI returned no completion");
            }
            String text = resp.choices().get(0).message() != null
                    ? resp.choices().get(0).message().content()
                    : "";
            int pt = resp.usage() != null ? resp.usage().prompt_tokens() : 0;
            int ct = resp.usage() != null ? resp.usage().completion_tokens() : 0;
            double cost = pt * CHAT_USD_PER_PROMPT_TOKEN + ct * CHAT_USD_PER_COMPLETION_TOKEN;
            return new Completion(text == null ? "" : text, pt, ct, props.getChatModel(), cost);
        } catch (RestClientResponseException e) {
            log.warn("OpenAI chat failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "ai_chat_failed",
                    "AI completion failed: " + e.getStatusText());
        }
    }

    // -- embed --------------------------------------------------------------

    @Override
    public List<float[]> embed(List<String> inputs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getEmbeddingModel());
        body.put("input", inputs);
        body.put("encoding_format", "float");

        try {
            EmbedResponse resp = http.post()
                    .uri("/v1/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(EmbedResponse.class);
            if (resp == null || resp.data() == null) {
                throw new KonvoException(HttpStatus.BAD_GATEWAY, "ai_empty_response",
                        "OpenAI returned no embeddings");
            }
            List<float[]> out = new ArrayList<>(resp.data().size());
            for (EmbedResponse.Item item : resp.data()) {
                float[] vec = new float[item.embedding().size()];
                for (int i = 0; i < vec.length; i++) {
                    vec[i] = item.embedding().get(i).floatValue();
                }
                out.add(vec);
            }
            return out;
        } catch (RestClientResponseException e) {
            log.warn("OpenAI embed failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "ai_embed_failed",
                    "AI embedding failed: " + e.getStatusText());
        }
    }

    /** USD estimate for an embed call, given the {@code prompt_tokens} usage. */
    public static double estimateEmbedCost(int tokens) {
        return tokens * EMBED_USD_PER_TOKEN;
    }

    private static String roleOf(CompletionRequest.Role role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
        };
    }

    record ChatResponse(List<Choice> choices, Usage usage) {
        record Choice(Msg message) {}
        record Msg(String role, String content) {}
        record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {}
    }

    record EmbedResponse(List<Item> data, Usage usage) {
        record Item(List<Double> embedding, int index) {}
        record Usage(int prompt_tokens, int total_tokens) {}
    }
}
