package com.finoffice.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * FinPilot 自研引擎模型装配（阶段2 升级版）：
 * - knowledgeChatModel（@Primary）：本地 Ollama Qwen2.5，数据不出域安全通道
 * 仅 finpilot.engine.enabled=true 时装配；未装配时全部问答自动回落 Dify 链路。
 */
@Configuration
@ConditionalOnProperty(prefix = "finpilot.engine", name = "enabled", havingValue = "true")
public class LangChain4jConfig {

    /** 本地 Ollama Qwen2.5 模型（数据不出域安全通道） */
    @Bean
    @Primary
    public ChatModel localChatModel(
            @Value("${finpilot.rag.ollama-base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${finpilot.rag.local-model:qwen2.5:latest}") String localModel,
            @Value("${finpilot.engine.timeout-seconds:120}") long timeoutSeconds) {
        return OpenAiChatModel.builder()
                .baseUrl(ollamaBaseUrl + "/v1")
                .apiKey("ollama")  // Ollama 不校验 key，但不能为空
                .modelName(localModel)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
}
