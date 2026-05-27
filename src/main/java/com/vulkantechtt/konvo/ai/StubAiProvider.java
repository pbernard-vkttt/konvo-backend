package com.vulkantechtt.konvo.ai;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-network stub. Active when {@code konvo.ai.default-provider=stub} (the
 * M1 default) — implements both the chat and embedding interfaces so the
 * full M5 RAG flow runs end-to-end against zero external APIs.
 *
 * Embedding strategy: SHA-256 of the input → expand bytes into 1536 floats
 * in [-1, 1]. Deterministic per input string, so the same text always lands
 * on the same vector and retrieval ordering is stable in tests.
 */
@Component
@ConditionalOnProperty(prefix = "konvo.ai", name = "default-provider", havingValue = "stub", matchIfMissing = true)
public class StubAiProvider implements AiCompletionProvider, AiEmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(StubAiProvider.class);
    private static final int DIMS = 1536;

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public int dimensions() {
        return DIMS;
    }

    @Override
    public Completion complete(CompletionRequest request) {
        String user = request.userMessage() == null ? "" : request.userMessage().toLowerCase();
        String reply;
        if (user.contains("hours") || user.contains("open")) {
            reply = "We open from 6 am to 10 pm every day. Sunday breakfast crowd 'till noon.";
        } else if (user.contains("price") || user.contains("cost")) {
            reply = "Pricing depends on what you need — what allyuh looking for?";
        } else if (user.isBlank()) {
            reply = "Heyy 👋 I'm Vee. Ask me anything.";
        } else {
            reply = "Got it. Let me check on that for you.";
        }
        log.info("[stub-ai] canned reply len={}", reply.length());
        return new Completion(reply, 0, reply.length() / 4, "stub-v1", 0.0);
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        List<float[]> out = new ArrayList<>(inputs.size());
        for (String input : inputs) {
            out.add(deterministicEmbedding(input == null ? "" : input));
        }
        return out;
    }

    /** SHA-256 → 1536 floats in [-1, 1]: tile the 32-byte digest 48× and
     *  rescale each byte. Cheap, deterministic, good enough that cosine
     *  distance gives a stable-ish ordering for tests against the stub. */
    static float[] deterministicEmbedding(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            float[] out = new float[DIMS];
            for (int i = 0; i < DIMS; i++) {
                int b = digest[i % digest.length] & 0xff;
                out[i] = (b / 127.5f) - 1.0f;
            }
            return out;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
