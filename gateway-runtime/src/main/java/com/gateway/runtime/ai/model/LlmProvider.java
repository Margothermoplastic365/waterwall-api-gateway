package com.gateway.runtime.ai.model;

/**
 * Supported LLM providers for universal routing.
 */
public enum LlmProvider {
    OPENAI,
    ANTHROPIC,
    GOOGLE_GEMINI,
    DEEPSEEK,
    AWS_BEDROCK,
    AZURE_OPENAI,
    MISTRAL,
    SELF_HOSTED
}
