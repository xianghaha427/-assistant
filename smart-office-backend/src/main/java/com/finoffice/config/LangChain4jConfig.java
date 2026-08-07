package com.finoffice.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * FinPilot 自研知识引擎装配（阶段1）：
 * LangChain4j OpenAiChatModel 走 OpenAI 兼容协议直连 DeepSeek。
 * 仅 finpilot.engine.enabled=true 时装配；未装配时知识问答自动回落 Dify 链路。
 * API Key 只读 application.yml 的 deepseek.api-key，严禁写入 git/代码/文档。
 * 注：LangChain4j 1.13 起聊天模型接口为 ChatModel（旧版 ChatLanguageModel 已移除）。
 */
@Configuration
@ConditionalOnProperty(prefix = "finpilot.engine", name = "enabled", havingValue = "true")
public class LangChain4jConfig {

    @Bean
    public ChatModel knowledgeChatModel(
            @Value("${deepseek.api-key:}") String apiKey,
            @Value("${deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${deepseek.model:deepseek-chat}") String modelName,
            @Value("${finpilot.engine.timeout-seconds:120}") long timeoutSeconds) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
}
