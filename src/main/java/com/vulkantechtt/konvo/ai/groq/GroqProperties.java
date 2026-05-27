package com.vulkantechtt.konvo.ai.groq;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "konvo.ai.groq")
public class GroqProperties {

    private String apiKey;

    /** Groq's OpenAI-compatible base. */
    private String baseUrl = "https://api.groq.com/openai";

    /** Fast + cheap default — Llama 3.1 8B; bump to a larger model when needed. */
    private String chatModel = "llama-3.1-8b-instant";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }
}
