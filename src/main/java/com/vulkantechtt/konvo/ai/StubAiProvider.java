package com.vulkantechtt.konvo.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Canned-response AI provider for development. Returns a deterministic Vee-style
 * reply so the inbox UI and AI reply queue can be exercised without an API key.
 *
 * Active when konvo.ai.default-provider=stub (M1 default).
 */
@Component
@ConditionalOnProperty(prefix = "konvo.ai", name = "default-provider", havingValue = "stub", matchIfMissing = true)
public class StubAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(StubAiProvider.class);

    @Override
    public String name() {
        return "stub";
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
        log.info("[stub-ai] returning canned reply (len={})", reply.length());
        return new Completion(reply, 0, reply.length() / 4, "stub-v1", 0.0);
    }
}
