package com.finoffice.service.kb;

import com.finoffice.entity.AgentConfig;
import com.finoffice.mapper.AgentConfigMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自研知识问答引擎（阶段1）：
 * LangChain4j ChatModel 直连 DeepSeek 生成回答，数据不出域（不经 Dify 云端）；
 * system prompt 取 agent_config id=1 的 system_prompt，为 NULL 时使用默认提示词
 * （与 SchemaMigrationRunner 回填值一致）。
 * 本组件常驻装配；模型 Bean 缺失（引擎开关未开启）时抛异常，由 Router 层捕获回落 Dify。
 * 注：LangChain4j 1.13 起聊天模型接口为 ChatModel（旧版 ChatLanguageModel 已移除）。
 */
@Slf4j
@Component
public class NativeKnowledgeEngine {

    /** 企业知识问答助手在 agent_config 中的 ID */
    private static final long AGENT_ID_KNOWLEDGE = 1L;

    /** 默认系统提示词（与 SchemaMigrationRunner 迁移回填值保持一致） */
    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是华信证券的企业知识问答助手。请严格基于给定的上下文资料回答问题："
                    + "资料中有明确依据时，给出简洁、专业、结构化的中文回答；"
                    + "资料中没有依据或无法确定时，坦承说明知识库中暂无相关内容，不要编造。";

    private final ObjectProvider<ChatModel> modelProvider;
    private final AgentConfigMapper agentConfigMapper;

    public NativeKnowledgeEngine(ObjectProvider<ChatModel> modelProvider,
                                 AgentConfigMapper agentConfigMapper) {
        this.modelProvider = modelProvider;
        this.agentConfigMapper = agentConfigMapper;
    }

    /** 自研引擎回答结果：answer + 引用列表（本期纯模型直答、无检索，citations 恒为 null） */
    public record EngineAnswer(String answer, List<String> citations) {}

    /**
     * 调用自研引擎生成回答。
     *
     * @throws IllegalStateException 模型 Bean 未装配（finpilot.engine.enabled 未开启）
     * @throws RuntimeException      模型调用异常（网络/超时/鉴权等），由 Router 捕获回落
     */
    public EngineAnswer answer(Long userId, String query) {
        ChatModel model = modelProvider.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("自研引擎未装配（finpilot.engine.enabled 未开启）");
        }
        String systemPrompt = DEFAULT_SYSTEM_PROMPT;
        try {
            AgentConfig agent = agentConfigMapper.selectById(AGENT_ID_KNOWLEDGE);
            if (agent != null && agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
                systemPrompt = agent.getSystemPrompt();
            }
        } catch (Exception e) {
            log.warn("读取 agent#1 system_prompt 失败，使用默认提示词: {}", e.getMessage());
        }
        ChatResponse response = model.chat(SystemMessage.from(systemPrompt), UserMessage.from(query));
        String text = response.aiMessage().text();
        return new EngineAnswer(text, null);
    }
}
