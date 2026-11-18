package com.sentinelqa.framework.ai;

import java.time.Duration;

import com.sentinelqa.framework.Config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Single LLM entry point. Talks to any OpenAI-compatible endpoint (OmniRoute, OpenRouter, Groq,
 * Gemini, Ollama...). AI is optional: with AI_BASE_URL unset every AI feature switches off and the
 * suite behaves like a plain Selenium/TestNG suite.
 */
public final class Ai {

    private static final ChatModel MODEL = create();

    private Ai() {}

    public static boolean enabled() {
        return MODEL != null;
    }

    /** Typed AI service: LangChain4j turns the interface's prompts and return type into LLM calls. */
    public static <T> T service(Class<T> api) {
        if (MODEL == null) throw new IllegalStateException("AI is disabled: set AI_BASE_URL");
        return AiServices.create(api, MODEL);
    }

    private static ChatModel create() {
        String baseUrl = Config.get("ai.base.url", "");
        if (baseUrl.isBlank()) return null;
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(Config.get("ai.api.key", "not-needed")) // local gateways such as Ollama ignore it
                .modelName(Config.get("ai.model", "auto"))
                .temperature(0.0)
                .timeout(Duration.ofSeconds(Config.getInt("ai.timeout.seconds", 60)))
                .maxRetries(1)
                .build();
    }
}
