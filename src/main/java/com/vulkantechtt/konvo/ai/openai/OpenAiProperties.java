package com.vulkantechtt.konvo.ai.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "konvo.ai.openai")
public class OpenAiProperties {

    /** Set on the {@code openai} profile; blank on stub/groq runs. */
    private String apiKey;

    /** Base URL — overridable for Azure / proxy deployments. */
    private String baseUrl = "https://api.openai.com";

    /** Chat completion model. text-class small + cheap by default. */
    private String chatModel = "gpt-4o-mini";

    /** Embedding model. 1536 dims, matches V005's vector column. */
    private String embeddingModel = "text-embedding-3-small";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
}
