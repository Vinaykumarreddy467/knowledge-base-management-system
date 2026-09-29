package com.kbms.ai;

/** Provider boundary for answer generation. Implementations must fail loudly, never improvise. */
public interface LlmService {

    boolean isConfigured();

    String model();

    /**
     * @param system strict grounding instruction
     * @param user   the fully rendered prompt, including delimited retrieved context
     * @return the model's answer text
     */
    String answer(String system, String user);
}
