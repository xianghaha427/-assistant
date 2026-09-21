package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.BizException;
import com.finoffice.config.SseHeartbeat;
import com.finoffice.entity.AgentConfig;
import com.finoffice.mapper.AgentConfigMapper;
import com.finoffice.service.DifyService;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * 办公写作助手（对应 agent_config id=5，Dify chat 型应用「办公写作助手」）
 * 一个入口覆盖三个写作场景：weekly 周报 / email 邮件 / notice 通知公告。
 * 按场景在消息头部拼入 [周报生成]/[邮件撰写]/[通知公告] 标签，
 * 与 dify-dsl/writing-assistant.yml 问题分类器的路由标签保持一致。
 *
 * 降级链（SSE 流式输出不中断）：
 *   1. Dify agent#5（chat SSE 转发）
 *   2. Mock 模板文案
 */
@Slf4j
@RestController
@RequestMapping("/api/writer")
@RequiredArgsConstructor
public class WritingController {

    private static final long AGENT_ID_WRITER = 5L;

    private final DifyService difyService;
    private final AgentConfigMapper agentConfigMapper;
    private final SseHeartbeat heartbeat;

    /** 共享有界线程池（AsyncConfig#aiSseExecutor），替换原无界 Executors.newCachedThreadPool() */
    @Resource(name = "aiSseExecutor")
    private ExecutorService executor;

    @Value("${dify.base-url:}")
    private String difyBaseUrl;

    @Data
    public static class WriterReq {
        /** 场景类型：weekly 周报 / email 邮件 / notice 通知公告 */
        private String type;
        /** 核心要点（必填）：本周工作要点 / 邮件诉求 / 公告要点 */
        private String points;
        /** 周报-下周计划（可选） */
        private String plan;
        /** 邮件收件人 / 公告主送对象（可选） */
        private String recipient;
        /** 邮件场景：汇报/请求/通知/致歉（可选） */
        private String scene;
        /** 公告标题事由（可选） */
        private String subject;
        /** 语气要求（可选） */
        private String tone;
    }

    /**
     * 生成写作文稿（SSE 流式返回）
     * 事件：source（命中的生成通道 dify/mock）、message（内容块）、done（结束）
     */
    @PostMapping(value = "/generate", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter generate(@RequestBody WriterReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        if (req.getType() == null || req.getType().isBlank()) {
            throw new BizException("写作场景类型不能为空");
        }
        if (req.getPoints() == null || req.getPoints().isBlank()) {
            throw new BizException("写作要点不能为空");
        }
        final String taggedMessage = buildTaggedMessage(req);

        SseEmitter emitter = new SseEmitter(150_000L);
        // 统一注册 onTimeout/onError/onCompletion 清理 + 15s 心跳（:ping）
        heartbeat.attach(emitter);
        executor.submit(() -> {
            // ---------- 优先级1：Dify agent#5（chat SSE 转发） ----------
            AgentConfig agent = null;
            try {
                agent = agentConfigMapper.selectById(AGENT_ID_WRITER);
            } catch (Exception e) {
                log.warn("查询写作助手智能体配置失败: {}", e.getMessage());
            }
            if (agent != null && agent.getDifyAppId() != null && !agent.getDifyAppId().isBlank()
                    && difyBaseUrl != null && !difyBaseUrl.isBlank()) {
                try {
                    sendSource(emitter, "dify");
                    difyService.streamChat(
                            agent.getDifyAppId(),
                            taggedMessage,
                            "",
                            userId,
                            chunk -> sendMessage(emitter, chunk),
                            convId -> { /* 工具型单次生成，不维护会话 */ },
                            () -> finish(emitter)
                    );
                    return;
                } catch (Exception e) {
                    log.warn("写作助手 Dify 调用失败，降级到 Mock: {}", e.getMessage());
                }
            } else {
                log.info("写作助手 agent#5 未配置 Dify 密钥或 Dify 地址为空，降级到 Mock");
            }

            // ---------- 优先级2：Mock 模板文案 ----------
            sendSource(emitter, "mock");
            sendMockStream(emitter, mockAnswer(req.getType(), req.getPoints()));
        });
        return emitter;
    }

    /**
     * 按场景组装带标签消息（标签与 Dify DSL 问题分类器路由标签一致）
     */
    private String buildTaggedMessage(WriterReq req) {
        StringBuilder sb = new StringBuilder();
        switch (req.getType()) {
            case "weekly" -> {
                sb.append("[周报生成] 本周工作要点：").append(req.getPoints().trim());
                if (notBlank(req.getPlan())) {
                    sb.append("\n下周工作计划：").append(req.getPlan().trim());
                }
            }
            case "email" -> {
                sb.append("[邮件撰写] 邮件诉求：").append(req.getPoints().trim());
                if (notBlank(req.getScene())) {
                    sb.append("\n邮件场景类型：").append(req.getScene().trim());
                }
                if (notBlank(req.getRecipient())) {
                    sb.append("\n收件人：").append(req.getRecipient().trim());
                }
            }
            case "notice" -> {
                sb.append("[通知公告] 公告要点：").append(req.getPoints().trim());
                if (notBlank(req.getSubject())) {
                    sb.append("\n通知事由/标题：").append(req.getSubject().trim());
                }
                if (notBlank(req.getRecipient())) {
                    sb.append("\n主送对象：").append(req.getRecipient().trim());
                }
            }
            default -> throw new BizException("不支持的写作场景类型：" + req.getType());
        }
        if (notBlank(req.getTone())) {
            sb.append("\n语气要求：").append(req.getTone().trim());
        }
        return sb.toString();
    }

    /** Mock 兜底模板文案（按场景） */
    private String mockAnswer(String type, String points) {
        return switch (type) {
            case "weekly" -> "一、本周工作总结\n1. " + points + "\n\n二、下周工作计划\n1. 持续推进本周未完成事项，确保按期交付。\n\n三、风险与需协调事项\n暂无\n\n（当前为演示模板文案，待 Dify/大模型服务接通后将由 AI 自动生成）";
            case "email" -> "主题：关于「" + brief(points) + "」的沟通邮件\n称呼：领导/同事：\n正文：您好！现就以下事项与您沟通：" + points + "，请审阅并反馈意见。\n【落款占位】\n\n（当前为演示模板文案，待 Dify/大模型服务接通后将由 AI 自动生成）";
            default -> "关于开展相关工作的通知\n各部门：\n现将 " + points + " 有关事项通知如下，请各部门遵照执行。\n【落款与日期占位】\n\n（当前为演示模板文案，待 Dify/大模型服务接通后将由 AI 自动生成）";
        };
    }

    private String brief(String text) {
        String t = text.trim().replaceAll("\\s+", " ");
        return t.length() > 20 ? t.substring(0, 20) + "…" : t;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private void sendSource(SseEmitter emitter, String source) {
        try {
            emitter.send(SseEmitter.event().name("source").data(source));
        } catch (IOException e) {
            log.warn("SSE 发送 source 事件失败: {}", e.getMessage());
        }
    }

    private void sendMessage(SseEmitter emitter, String chunk) {
        try {
            emitter.send(SseEmitter.event().name("message").data(chunk));
        } catch (IOException e) {
            log.warn("SSE 发送 message 块失败: {}", e.getMessage());
        }
    }

    private void finish(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    /** 以 SSE 流式方式分块发送完整文本（Mock 兜底） */
    private void sendMockStream(SseEmitter emitter, String answer) {
        try {
            for (String chunk : answer.split("(?<=。)|(?<=\n)")) {
                emitter.send(SseEmitter.event().name("message").data(chunk));
                Thread.sleep(90);
            }
            finish(emitter);
        } catch (IOException e) {
            emitter.completeWithError(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.complete();
        }
    }
}
