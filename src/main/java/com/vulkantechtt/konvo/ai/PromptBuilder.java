package com.vulkantechtt.konvo.ai;

import com.vulkantechtt.konvo.common.SafeText;
import com.vulkantechtt.konvo.knowledge.KnowledgeRetriever;
import java.util.List;

/**
 * Builds the Vee system prompt: a Trini-tuned persona with retrieved
 * knowledge-base chunks pasted as the grounding context. Kept here (not
 * inside AiReplyService) so the unit tests can lock the wording.
 */
public final class PromptBuilder {

    private static final String PERSONA = """
            You are Vee, a friendly Trinidad & Tobago customer-support assistant for the workspace named "%s".
            Reply in warm, plain Trini-English. Be concise — at most 2-3 short sentences.
            Use Trini conventions where natural: TT$ for currency, DMY for dates.
            Never invent facts. If the answer isn't in the context below, say you'll get the team to follow up.
            """;

    private PromptBuilder() {}

    public static String systemPrompt(String workspaceName, List<KnowledgeRetriever.Hit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append(PERSONA.formatted(SafeText.singleLine(workspaceName, "this workspace", 160)).strip())
                .append("\n\n");
        if (hits.isEmpty()) {
            sb.append("CONTEXT: (no knowledge base entries found for this query)");
        } else {
            sb.append("CONTEXT — answer from these snippets only:\n");
            for (int i = 0; i < hits.size(); i++) {
                KnowledgeRetriever.Hit h = hits.get(i);
                sb.append("[").append(i + 1).append("] from \"")
                        .append(h.sourceTitle() == null ? "untitled" : h.sourceTitle())
                        .append("\":\n")
                        .append(h.content().strip())
                        .append("\n\n");
            }
        }
        return sb.toString();
    }
}
