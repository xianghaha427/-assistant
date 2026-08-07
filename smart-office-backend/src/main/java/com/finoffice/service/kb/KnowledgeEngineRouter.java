package com.finoffice.service.kb;

import com.finoffice.common.OpLogger;
import com.finoffice.entity.AgentConfig;
import com.finoffice.mapper.AgentConfigMapper;
import com.finoffice.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 知识问答引擎路由（阶段1双通道）：
 * 读 agent_config#1 的 engine 列（'native'/'dify'，默认 dify）；
 * native 时调自研引擎；任何异常 / 空密钥 / Bean 缺失 → OpLogger + log.warn 留痕后返回 null，
 * 由调用方（KnowledgeService）回落现有 Dify 链路。留痕风格对齐 MeetingService fail_reason 模式。
 */
@Slf4j
@Component
public class KnowledgeEngineRouter {

    private static final long AGENT_ID_KNOWLEDGE = 1L;

    private final AgentConfigMapper agentConfigMapper;
    private final NativeKnowledgeEngine nativeEngine;

    @Value("${deepseek.api-key:}")
    private String deepseekApiKey;

    public KnowledgeEngineRouter(AgentConfigMapper agentConfigMapper, NativeKnowledgeEngine nativeEngine) {
        this.agentConfigMapper = agentConfigMapper;
        this.nativeEngine = nativeEngine;
    }

    /**
     * 尝试走自研引擎回答。
     *
     * @return 成功返回 source=native 的回答；未启用/失败返回 null（调用方回落 Dify）
     */
    public KnowledgeService.KnowledgeAnswer tryNative(Long userId, String query) {
        String engine;
        try {
            AgentConfig agent = agentConfigMapper.selectById(AGENT_ID_KNOWLEDGE);
            engine = (agent == null || agent.getEngine() == null || agent.getEngine().isBlank())
                    ? "dify" : agent.getEngine();
        } catch (Exception e) {
            log.warn("读取 agent#1 engine 配置失败，回落 Dify: {}", e.getMessage());
            return null;
        }
        if (!"native".equalsIgnoreCase(engine)) {
            return null;
        }
        if (deepseekApiKey == null || deepseekApiKey.isBlank()) {
            log.warn("知识问答 engine=native 但 DeepSeek 密钥为空，回落 Dify");
            OpLogger.log(userId, "KNOWLEDGE", "native-engine-fallback", "DeepSeek 密钥为空，知识问答回落 Dify");
            return null;
        }
        try {
            NativeKnowledgeEngine.EngineAnswer result = nativeEngine.answer(userId, query);
            if (result == null || result.answer() == null || result.answer().isBlank()) {
                log.warn("自研引擎返回空答案，回落 Dify");
                OpLogger.log(userId, "KNOWLEDGE", "native-engine-fallback", "自研引擎空回答，回落 Dify");
                return null;
            }
            OpLogger.log(userId, "KNOWLEDGE", "native-engine-answer",
                    "自研引擎回答成功 query=" + query.substring(0, Math.min(query.length(), 50)));
            return new KnowledgeService.KnowledgeAnswer(result.answer(), "native");
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            log.warn("自研引擎调用失败，回落 Dify: {}", msg);
            OpLogger.log(userId, "KNOWLEDGE", "native-engine-fallback",
                    "自研引擎异常回落 Dify: " + e.getClass().getSimpleName() + ": "
                            + msg.substring(0, Math.min(msg.length(), 200)));
            return null;
        }
    }
}
