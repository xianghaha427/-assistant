package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.BizException;
import com.finoffice.common.R;
import com.finoffice.config.DeepSeekConfig;
import com.finoffice.config.SseHeartbeat;
import com.finoffice.entity.AgentConfig;
import com.finoffice.entity.AiMessage;
import com.finoffice.entity.AiSession;
import com.finoffice.mapper.AgentConfigMapper;
import com.finoffice.service.AiSessionService;
import com.finoffice.service.DeepSeekService;
import com.finoffice.service.DifyService;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * AI 会话接口
 * 优先级：Dify > DeepSeek 直连 > Mock 兜底
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiSessionService aiSessionService;
    private final DeepSeekService deepSeekService;
    private final DifyService difyService;
    private final DeepSeekConfig deepSeekConfig;
    private final AgentConfigMapper agentConfigMapper;
    private final SseHeartbeat heartbeat;

    /** 共享有界线程池（AsyncConfig#aiSseExecutor），替换原无界 Executors.newCachedThreadPool() */
    @Resource(name = "aiSseExecutor")
    private ExecutorService executor;

    @Value("${dify.base-url:}")
    private String difyBaseUrl;

    @Data
    public static class SessionReq {
        private Long agentId;
        private String title;
    }

    @Data
    public static class ChatReq {
        private Long sessionId;
        private Long agentId;
        private String message;
    }

    /** 创建会话 */
    @PostMapping("/sessions")
    public R<AiSession> createSession(@RequestBody SessionReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(aiSessionService.createSession(userId, req.getAgentId(), req.getTitle()));
    }

    /** 我的会话列表（可选 agentId 过滤，如行业洞察页只列本智能体会话） */
    @GetMapping("/sessions")
    public R<List<AiSession>> listSessions(@RequestParam(required = false) Long agentId) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(aiSessionService.listByUser(userId, agentId));
    }

    /** 会话历史消息 */
    @GetMapping("/sessions/{id}/messages")
    public R<List<AiMessage>> messages(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(aiSessionService.listMessages(userId, id));
    }

    /** 删除会话（级联删除消息） */
    @DeleteMapping("/sessions/{id}")
    public R<Void> deleteSession(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        aiSessionService.deleteSession(userId, id);
        return R.ok();
    }

    /**
     * 发送消息（SSE 流式返回）
     * 优先级：Agent 配置了 difyAppId → Dify > DeepSeek > Mock 兜底
     */
    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestBody ChatReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        if (req.getMessage() == null || req.getMessage().isBlank()) {
            throw new BizException("消息不能为空");
        }

        // 未传会话则自动创建
        Long sessionId = aiSessionService.getOrCreateSession(userId, req.getSessionId(), req.getAgentId(), req.getMessage());

        // 保存用户消息
        aiSessionService.saveMessage(sessionId, "user", req.getMessage());

        final Long sid = sessionId;

        // 查询智能体配置（提前查询，workflow 型应用调用耗时较长，需放宽 SSE 超时）
        final AgentConfig agent = req.getAgentId() != null
                ? agentConfigMapper.selectById(req.getAgentId()) : null;
        boolean isWorkflow = agent != null && "workflow".equalsIgnoreCase(agent.getDifyMode());

        SseEmitter emitter = new SseEmitter(isWorkflow ? 200_000L : 60_000L);
        // 统一注册 onTimeout/onError/onCompletion 清理 + 15s 心跳（:ping）
        heartbeat.attach(emitter);
        try {
            // 首帧返回 sessionId，便于前端绑定会话
            emitter.send(SseEmitter.event().name("session").data(sid));
        } catch (IOException e) {
            throw new BizException("SSE 初始化失败");
        }

        // 优先级1：Agent 配置了 difyAppId 且 difyBaseUrl 有效 → 调用 Dify
        if (agent != null && agent.getDifyAppId() != null && !agent.getDifyAppId().isBlank()
                && difyBaseUrl != null && !difyBaseUrl.isBlank()) {
            // workflow 型应用（如会议纪要助手）：blocking 调用，结果整体作为一个 message 块发出
            if ("workflow".equalsIgnoreCase(agent.getDifyMode())) {
                runDifyWorkflow(emitter, sid, req, agent, userId);
                return emitter;
            }
            // 获取当前会话的 difyConversationId（用于多轮上下文）
            AiSession session = aiSessionService.getSessionById(sid);
            String difyConvId = session != null ? session.getDifyConversationId() : null;

            final String difyAppApiKey = agent.getDifyAppId();
            final String currentConvId = difyConvId;
            final StringBuilder fullAnswer = new StringBuilder();

            executor.submit(() -> {
                try {
                    difyService.streamChat(
                            difyAppApiKey,
                            req.getMessage(),
                            currentConvId,
                            userId,
                            // onChunk：每收到一段内容就通过 SSE 推送给前端
                            chunk -> {
                                try {
                                    fullAnswer.append(chunk);
                                    emitter.send(SseEmitter.event().name("message").data(chunk));
                                } catch (IOException e) {
                                    log.warn("SSE 发送 Dify chunk 失败: {}", e.getMessage());
                                }
                            },
                            // onConversationId：首次收到 conversation_id 时保存到会话
                            convId -> {
                                try {
                                    aiSessionService.updateDifyConversationId(sid, convId);
                                } catch (Exception e) {
                                    log.warn("保存 difyConversationId 失败: {}", e.getMessage());
                                }
                            },
                            // onComplete：流结束后保存消息并发送 done 事件
                            () -> {
                                try {
                                    String answer = fullAnswer.toString();
                                    aiSessionService.saveMessage(sid, "assistant", answer);
                                    aiSessionService.incrementMsgCount(sid, 2);
                                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                    emitter.complete();
                                } catch (IOException e) {
                                    emitter.completeWithError(e);
                                }
                            }
                    );
                } catch (Exception e) {
                    // Dify 调用失败，降级到 DeepSeek
                    log.warn("Dify API 调用失败，降级到 DeepSeek: {}", e.getMessage());
                    fallbackToDeepSeek(emitter, sid, req, agent);
                }
            });
            return emitter;
        }

        // 优先级2：DeepSeek 直连
        if (deepSeekConfig.isEnabled() && deepSeekConfig.getApiKey() != null
                && !deepSeekConfig.getApiKey().isBlank()) {
            fallbackToDeepSeek(emitter, sid, req, agent);
            return emitter;
        }

        // 优先级3：Mock 兜底
        sendMockStream(emitter, sid, mockAnswer(req.getMessage()));
        return emitter;
    }

    /**
     * 调用 Dify Workflow 型应用（blocking 模式），结果一次性作为一个 message 块发出，
     * 失败时复用现有 DeepSeek/Mock 降级框架
     */
    private void runDifyWorkflow(SseEmitter emitter, Long sid, ChatReq req, AgentConfig agent, long userId) {
        final String difyAppApiKey = agent.getDifyAppId();
        executor.submit(() -> {
            try {
                String inputVar = agent.getInputVariable();
                if (inputVar == null || inputVar.isBlank()) {
                    inputVar = "input";
                }
                String result = difyService.runWorkflow(difyAppApiKey, java.util.Map.of(inputVar, req.getMessage()), userId);
                aiSessionService.saveMessage(sid, "assistant", result);
                aiSessionService.incrementMsgCount(sid, 2);
                emitter.send(SseEmitter.event().name("message").data(result));
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            } catch (Exception e) {
                // Dify Workflow 调用失败，降级到 DeepSeek
                log.warn("Dify Workflow 调用失败，降级到 DeepSeek: {}", e.getMessage());
                fallbackToDeepSeek(emitter, sid, req, agent);
            }
        });
    }
    /**
     * 降级到 DeepSeek 直连
     */
    private void fallbackToDeepSeek(SseEmitter emitter, Long sid, ChatReq req, AgentConfig agent) {
        String systemPrompt = "你是一个专业的企业办公助手，请用简洁、专业的方式回答用户问题。";
        if (agent != null && agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
            systemPrompt = agent.getSystemPrompt();
        }

        final String sysPrompt = systemPrompt;
        final StringBuilder fullAnswer = new StringBuilder();

        executor.submit(() -> {
            try {
                deepSeekService.streamChat(
                        sysPrompt,
                        req.getMessage(),
                        chunk -> {
                            try {
                                fullAnswer.append(chunk);
                                emitter.send(SseEmitter.event().name("message").data(chunk));
                            } catch (IOException e) {
                                log.warn("SSE 发送 chunk 失败: {}", e.getMessage());
                            }
                        },
                        () -> {
                            try {
                                String answer = fullAnswer.toString();
                                aiSessionService.saveMessage(sid, "assistant", answer);
                                aiSessionService.incrementMsgCount(sid, 2);
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        }
                );
            } catch (Exception e) {
                // DeepSeek 调用也失败，降级到 Mock 兜底
                log.warn("DeepSeek API 调用失败，降级到 Mock 兜底: {}", e.getMessage());
                sendMockStream(emitter, sid, mockAnswer(req.getMessage()));
            }
        });
    }

    /**
     * 以 SSE 流式方式发送一段完整文本（Mock 兜底逻辑）
     */
    private void sendMockStream(SseEmitter emitter, Long sid, String answer) {
        executor.submit(() -> {
            try {
                for (String chunk : answer.split("(?<=。)|(?<=\n)")) {
                    emitter.send(SseEmitter.event().name("message").data(chunk));
                    Thread.sleep(120);
                }
                aiSessionService.saveMessage(sid, "assistant", answer);
                aiSessionService.incrementMsgCount(sid, 2);
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.complete();
            }
        });
    }

    private String mockAnswer(String question) {
        return "你好，我是平台内置的演示助手。\n"
                + "当前 Dify 大模型服务尚未接入，这是一条模拟回复，用于验证前后端流式对话链路。\n"
                + "你刚才问的是：「" + question + "」。\n"
                + "待管理员配置 Dify 地址与 API Key 后，这里将由真实大模型结合企业知识库进行回答，并附带引用来源。";
    }
}
