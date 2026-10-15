package com.finoffice.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * FinPilot 工具调用编排模型装配：
 * 为 ToolUseAgentService（POST /api/ai/agent-exec）提供一个支持 function calling 的
 * OpenAI 兼容 ChatModel，使其能自主调用 AgentTools 上的 @Tool 方法。
 *
 * 端点完全由环境变量决定，不落配置文件：
 *   FINPILOT_MODEL_BASE_URL  必填，未配置则本配置类整体不生效
 *   FINPILOT_MODEL_API_KEY   选填，部分网关不校验
 *   FINPILOT_MODEL_NAME      必填，模型名
 * 未配置 base-url 时 Bean 不存在；ToolUseAgentService 以 ObjectProvider 注入，
 * 缺失时走"自研引擎未启用"降级（ToolUseAgentService.java:86-89），不影响平台启动。
 *
 * 条件为什么不用 @ConditionalOnProperty：docker-compose.yml 用
 * {@code ${FINPILOT_MODEL_BASE_URL:-}} 透传，未配置时容器里该变量是**空字符串**而非不存在，
 * 而 @ConditionalOnProperty 不带 havingValue 时只排除 "false"，空串会被判为"已配置"
 * 从而错误装配出一个 baseUrl 为空的模型。这里用 SpEL 显式判空，两种情形都能正确跳过。
 */
@Configuration
@ConditionalOnExpression("'${finpilot.model.base-url:}'.trim().length() > 0")
public class LangChain4jConfig {

    /** 工具调用编排模型（OpenAI 兼容协议，端点由环境变量提供） */
    @Bean
    @Primary
    public ChatModel toolCallChatModel(
            @Value("${finpilot.model.base-url}") String baseUrl,
            @Value("${finpilot.model.api-key:}") String apiKey,
            @Value("${finpilot.model.model-name}") String modelName,
            @Value("${finpilot.model.timeout-seconds:120}") long timeoutSeconds) {
        // 客户端不允许空 key：多数自建网关/本地推理服务不校验，但不能为空
        String effectiveApiKey = (apiKey == null || apiKey.isBlank()) ? "not-required" : apiKey;
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(effectiveApiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
}
