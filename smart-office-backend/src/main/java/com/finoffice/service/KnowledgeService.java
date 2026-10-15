package com.finoffice.service;

import com.finoffice.common.BizException;
import com.finoffice.entity.AgentConfig;
import com.finoffice.mapper.AgentConfigMapper;
import com.finoffice.service.kb.LocalDocumentRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 知识库服务。提供两种互相独立的问答模式：
 *
 * <p>1）Dify 模式（默认，{@link #searchInKnowledge}）：直连 Dify 智能体「企业知识问答助手」
 * （agent_config id=1，chat 模式）。回答完全来自该 App 自带的 Dify 知识库，
 * 与平台「文档管理」里上传的文档无关——向 Dify 只发送 query。
 * 密钥仅存于数据库 agent_config.dify_app_id，不落配置文件。
 * Dify 失败时返回明确的不可用提示，不依赖本地模型或本地推理服务。
 *
 * <p>2）本地文档模式（{@link #searchLocal}）：只检索**当前用户有权限访问的**、
 * 平台上上传的文档，由 {@link LocalDocumentRetriever} 实现。未配置模型端点时该模式整体关闭。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    /** 企业知识问答助手在 agent_config 中的 ID */
    private static final long AGENT_ID_KNOWLEDGE = 1L;

    private final AgentConfigMapper agentConfigMapper;
    private final DifyService difyService;
    /** 本地文档检索（新增模式）；与下面的 Dify 路径完全独立 */
    private final LocalDocumentRetriever localDocumentRetriever;

    @Value("${dify.base-url:}")
    private String difyBaseUrl;

    /**
     * 知识库问答结果（answer + 来源判定 dify/mock）
     */
    public record KnowledgeAnswer(String answer, String source) {}

    /**
     * 知识库问答：复用 DifyService.streamChat（URL 归一化、SSE 解析、连接重试），
     * chat 模式 query 即 Dify 应用的 sys.query，blocking 聚合为完整回答后返回
     */
    public KnowledgeAnswer searchInKnowledge(Long userId, String query) {
        if (query == null || query.isBlank()) {
            throw new BizException("查询内容不能为空");
        }

        AgentConfig agent;
        try {
            agent = agentConfigMapper.selectById(AGENT_ID_KNOWLEDGE);
        } catch (Exception e) {
            log.warn("查询知识问答智能体配置失败: {}", e.getMessage());
            agent = null;
        }

        if (agent != null && agent.getDifyAppId() != null && !agent.getDifyAppId().isBlank()
                && difyBaseUrl != null && !difyBaseUrl.isBlank()) {
            try {
                StringBuilder full = new StringBuilder();
                CountDownLatch latch = new CountDownLatch(1);
                // chat 型应用：query 对应 Dify 应用 sys.query；单次问答不维护会话（conversationId 传空）
                difyService.streamChat(
                        agent.getDifyAppId(),
                        query,
                        "",
                        userId,
                        full::append,
                        convId -> { /* 知识问答不维护多轮会话 */ },
                        latch::countDown
                );
                if (!latch.await(120, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Dify 知识问答超时");
                }
                String answer = full.toString();
                if (answer.isBlank()) {
                    return new KnowledgeAnswer("知识库中未找到相关内容，请换个问法试试。", "dify");
                }
                return new KnowledgeAnswer(answer, "dify");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("知识问答被中断，降级到演示文案");
            } catch (Exception e) {
                // 三级降级：Dify 失败 → 演示提示文案（与其他模块保持一致）
                log.warn("Dify 知识问答失败，降级到演示文案: {}", e.getMessage());
            }
        } else {
            log.info("知识问答 agent#1 未配置 Dify 密钥或 Dify 地址为空，降级到演示文案");
        }

        return new KnowledgeAnswer(
                "当前知识库问答服务暂时不可用（Dify 调用失败或未配置），请稍后重试或联系管理员。",
                "mock");
    }

    /**
     * 本地文档问答（新增模式）：只检索**当前用户有权限访问的、平台上上传的**文档。
     *
     * 与上面的 searchInKnowledge 完全独立：不读 Dify 配置、不碰 agent_config，
     * 也不改动 KnowledgeAnswer 的结构——出处在回答正文里由模型标注，
     * 因此现有调用点与响应格式一字不变。
     *
     * @param query 用户问题；与 Dify 路径同样要求非空
     */
    public KnowledgeAnswer searchLocal(Long userId, String query) {
        if (query == null || query.isBlank()) {
            throw new BizException("查询内容不能为空");
        }
        LocalDocumentRetriever.Result result = localDocumentRetriever.retrieve(userId, query);
        return new KnowledgeAnswer(result.answer(), result.source());
    }

    /** 本地文档模式是否可用（未配置模型端点时为 false）；前端据此置灰开关 */
    public boolean isLocalModeEnabled() {
        return localDocumentRetriever.isEnabled();
    }
}
