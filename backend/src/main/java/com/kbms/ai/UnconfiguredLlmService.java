package com.kbms.ai;

import com.kbms.common.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Placeholder for a non-OpenAI provider boundary. Every call reports the missing configuration. */
@Component
@ConditionalOnProperty(name = "kbms.llm.provider", havingValue = "local")
public class UnconfiguredLlmService implements LlmService {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String model() {
        return "none";
    }

    @Override
    public String answer(String system, String user) {
        throw ApiException.unavailable("kbms.llm.provider=local has no model bound. Set KBMS_LLM_PROVIDER=openai "
                + "with KBMS_LLM_BASE_URL, KBMS_LLM_API_KEY and KBMS_LLM_MODEL.");
    }
}
