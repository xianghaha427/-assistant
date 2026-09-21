package com.finoffice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Dify 流式对话服务
 * 使用 JDK 原生 HttpClient 调用 Dify Chat API（SSE 流式）与 Workflow API（blocking 阻塞式）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifyService {

    private final ObjectMapper objectMapper;

    @Value("${dify.base-url:http://localhost/v1}")
    private String difyBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 流式对话（Dify Chat API）
     *
     * @param difyAppApiKey    智能体绑定的 Dify 应用 API Key（即 agent.difyAppId）
     * @param query            用户消息
     * @param conversationId   会话 ID（首次为空，后续传入以维持上下文）
     * @param userId           用户 ID
     * @param onChunk          每收到一段内容时的回调
     * @param onConversationId 首次收到 conversation_id 时的回调
     * @param onComplete       流结束时的回调
     */
    public void streamChat(String difyAppApiKey, String query, String conversationId, Long userId,
                           Consumer<String> onChunk, Consumer<String> onConversationId, Runnable onComplete) {
        streamChat(difyAppApiKey, query, conversationId, userId, null, onChunk, onConversationId, onComplete);
    }

    /**
     * 流式对话（携带文档文件）：files 为 Dify local_file 传输方式的描述列表，
     * 每项 {type:"document", transfer_method:"local_file", upload_file_id:xxx}，
     * upload_file_id 来自 files/upload 接口；传 null 即普通对话。
     */
    public void streamChat(String difyAppApiKey, String query, String conversationId, Long userId,
                           List<Map<String, Object>> files,
                           Consumer<String> onChunk, Consumer<String> onConversationId, Runnable onComplete) {
        String url = buildUrl("/chat-messages");

        // 构造请求体 JSON
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(new DifyChatRequest(
                    query,
                    conversationId != null ? conversationId : "",
                    "user-" + userId,
                    files
            ));
        } catch (Exception e) {
            throw new RuntimeException("构造 Dify 请求体失败", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + difyAppApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        // 连接重置等瞬时连接异常限重试 1 次（仅尚未输出任何内容时允许，防止重复输出）
        AtomicBoolean chunkSent = new AtomicBoolean(false);
        Consumer<String> guardedChunk = chunk -> {
            chunkSent.set(true);
            onChunk.accept(chunk);
        };
        for (int attempt = 0; attempt < 2; attempt++) {
        // 剥离 Dify 应用内模型的深度思考输出 <think>...</think>，仅透传正式回答
        ThinkingFilter thinkingFilter = new ThinkingFilter();
        try {
            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("Dify API 返回非 200 状态码: {}，响应: {}", response.statusCode(), body);
                throw new RuntimeException("Dify API 调用失败，状态码: " + response.statusCode());
            }

            // 逐行解析 SSE 流
            AtomicBoolean conversationIdSent = new AtomicBoolean(false);
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if (data.isEmpty()) {
                            continue;
                        }
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            String event = node.path("event").asText("");

                            // 首次收到 conversation_id 时回调
                            if (!conversationIdSent.get()) {
                                String convId = node.path("conversation_id").asText(null);
                                if (convId != null && !convId.isEmpty()) {
                                    conversationIdSent.set(true);
                                    onConversationId.accept(convId);
                                }
                            }

                            if ("message".equals(event)) {
                                String answer = node.path("answer").asText(null);
                                if (answer != null && !answer.isEmpty()) {
                                    String filtered = thinkingFilter.feed(answer);
                                    if (filtered != null && !filtered.isEmpty()) {
                                        guardedChunk.accept(filtered);
                                    }
                                }
                            } else if ("message_end".equals(event)) {
                                String tail = thinkingFilter.flush();
                                if (tail != null && !tail.isEmpty()) {
                                    guardedChunk.accept(tail);
                                }
                                onComplete.run();
                                return;
                            } else if ("error".equals(event)) {
                                String errMsg = node.path("message").asText("未知错误");
                                log.error("Dify 返回错误事件: {}", errMsg);
                                throw new RuntimeException("Dify 流式响应错误: " + errMsg);
                            }
                        } catch (RuntimeException re) {
                            throw re;
                        } catch (Exception parseEx) {
                            log.warn("解析 Dify SSE 数据失败: {}", data, parseEx);
                        }
                    }
                }
                // 流自然结束（未收到 message_end 也视为完成）
                String tail = thinkingFilter.flush();
                if (tail != null && !tail.isEmpty()) {
                    guardedChunk.accept(tail);
                }
                onComplete.run();
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Dify 流式请求被中断", e);
        } catch (java.io.IOException e) {
            // 连接级异常（Connection reset / 超时等）：未输出过内容时重试 1 次，提升稳定性
            if (attempt == 0 && !chunkSent.get()) {
                log.warn("Dify 流式连接异常（{}），重试一次", e.getMessage());
                continue;
            }
            throw new RuntimeException("Dify 流式请求异常: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Dify 流式请求异常: " + e.getMessage(), e);
        }
        }
    }


    /**
     * 阻塞式调用 Dify Workflow 应用（会议纪要、PPT大纲生成 等 workflow 型智能体）
     * 输入变量名按应用而异，由调用方依据 agent 配置（inputVariable）传入
     *
     * @param difyAppApiKey 智能体绑定的 Dify 应用 API Key（即 agent.difyAppId）
     * @param inputs        workflow 输入参数映射（变量名 -> 值）
     * @param userId        用户 ID
     * @return workflow 生成的结果文本（data.outputs.result）
     */
    public String runWorkflow(String difyAppApiKey, java.util.Map<String, String> inputs, Long userId) {
        String url = buildUrl("/workflows/run");

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(new DifyWorkflowRequest(
                    new java.util.HashMap<String, Object>(inputs),
                    "blocking",
                    String.valueOf(userId)
            ));
        } catch (Exception e) {
            throw new RuntimeException("构造 Dify Workflow 请求体失败", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                // blocking 模式下 LLM 生成纪要可能耗时 30-60 秒，超时放宽至 180 秒
                .timeout(Duration.ofSeconds(180))
                .header("Authorization", "Bearer " + difyAppApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.error("Dify Workflow API 返回非 200 状态码: {}，响应: {}", response.statusCode(), response.body());
                throw new RuntimeException("Dify Workflow API 调用失败，状态码: " + response.statusCode());
            }

            JsonNode outputs = objectMapper.readTree(response.body()).path("data").path("outputs");
            String result = outputs.path("result").asText(null);
            if (result == null || result.isBlank()) {
                // 兜底：result 缺失时，取 outputs 中第一个非空字符串值
                var fields = outputs.fields();
                while (fields.hasNext()) {
                    JsonNode value = fields.next().getValue();
                    if (value.isTextual() && !value.asText().isBlank()) {
                        result = value.asText();
                        break;
                    }
                }
            }
            if (result == null || result.isBlank()) {
                throw new RuntimeException("Dify Workflow 未返回有效结果");
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Dify Workflow 请求被中断", e);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Dify Workflow 请求异常: " + e.getMessage(), e);
        }
    }

    /**
     * 文件上传（Dify files/upload）：返回 file_id，供 chat-messages 的 files 参数引用
     *
     * @param difyAppApiKey 目标 Dify 应用的 API Key
     * @param fileName      原始文件名
     * @param bytes         文件二进制内容
     * @param userId        用户 ID（与 streamChat 的 user 保持一致："user-" 前缀）
     */
    public String uploadFile(String difyAppApiKey, String fileName, byte[] bytes, Long userId) {
        String url = buildUrl("/files/upload");
        String boundary = "----FinPilotBoundary" + System.nanoTime();
        byte[] body;
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            bos.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"user\"\r\n\r\n"
                    + "user-" + userId + "\r\n").getBytes(StandardCharsets.UTF_8));
            bos.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                    + fileName + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            bos.write(bytes);
            bos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            body = bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("构造上传请求体失败: " + e.getMessage(), e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + difyAppApiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        // 连接级异常（Connection reset / 超时等 IOException）限重试 1 次，间隔 500ms；
        // 与 streamChat 的连接重试策略一致，瞬时网络抖动对用户无感
        Exception lastEx = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            if (attempt > 1) {
                try { Thread.sleep(500L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                log.warn("Dify 文件上传连接异常，500ms 后重试第{}次: {}", attempt, lastEx.getMessage());
            }
            try {
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200 && response.statusCode() != 201) {
                    log.error("Dify 文件上传返回非2xx状态码: {}，响应: {}", response.statusCode(), response.body());
                    throw new RuntimeException("Dify 文件上传失败，状态码: " + response.statusCode());
                }
                String fileId = objectMapper.readTree(response.body()).path("id").asText(null);
                if (fileId == null || fileId.isBlank()) {
                    throw new RuntimeException("Dify 文件上传响应缺少 id 字段");
                }
                return fileId;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Dify 文件上传被中断", e);
            } catch (java.io.IOException e) {
                // 连接被重置等瞬时异常：允许重试 1 次
                lastEx = e;
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("Dify 文件上传异常: " + e.getMessage(), e);
            }
        }
        throw new RuntimeException("Dify 文件上传异常(已重试1次): " + lastEx.getMessage(), lastEx);
    }

    /**
     * Build Dify API url: normalize base-url to avoid duplicated /v1 segment.
     */
    private String buildUrl(String path) {
        String base = difyBaseUrl == null ? "" : difyBaseUrl.replaceAll("/+$", "");
        if (base.endsWith("/v1")) {
            return base + path;
        }
        return base + "/v1" + path;
    }

    /**
     * Dify Workflow API 请求体内部 DTO
     */
    private record DifyWorkflowRequest(java.util.Map<String, Object> inputs, String response_mode, String user) {
    }

    /**
     * Dify Chat API 请求体内部 DTO（files：携带文档时的 local_file 描述列表，无文件时不序列化该字段）
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private record DifyChatRequest(Object inputs, String query, String response_mode,
                                   String conversation_id, String user, List<Map<String, Object>> files) {
        DifyChatRequest(String query, String conversationId, String user, List<Map<String, Object>> files) {
            this(new java.util.HashMap<>(), query, "streaming", conversationId, user,
                    files == null || files.isEmpty() ? null : files);
        }
    }

    /**
     * 流式剥离 Dify 应用内模型的深度思考输出。
     * 开启深度思考的模型会把推理过程用 {@code <think>...</think>} 包裹
     * 拼在正式回答之前；
     * 本过滤器按标签边界剥离，仅透传 {@code <think>} 之外的正式回答内容。
     */
    private static final class ThinkingFilter {
        private static final String OPEN = "<think>";
        private static final String CLOSE = "</think>";
        private final StringBuilder carry = new StringBuilder();
        private boolean inThink = false;

        /** 输入一段流式文本，返回应对外输出的正式回答部分（可能为 null 表示本次无输出）。 */
        String feed(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return null;
            }
            carry.append(chunk);
            String buf = carry.toString();
            StringBuilder out = new StringBuilder();
            int idx = 0;
            while (idx < buf.length()) {
                if (!inThink) {
                    int open = buf.indexOf(OPEN, idx);
                    if (open >= 0) {
                        out.append(buf, idx, open);
                        inThink = true;
                        idx = open + OPEN.length();
                    } else {
                        // 无完整 <think>：输出到安全位置，末尾保留可能被拆分的 <think> 前缀
                        int safeEnd = Math.max(idx, buf.length() - (OPEN.length() - 1));
                        out.append(buf, idx, safeEnd);
                        idx = safeEnd;
                        break;
                    }
                } else {
                    int close = buf.indexOf(CLOSE, idx);
                    if (close >= 0) {
                        inThink = false;
                        idx = close + CLOSE.length();
                    } else {
                        // 无完整 </think>：全部丢弃，末尾保留可能被拆分的 </think> 前缀
                        idx = Math.max(idx, buf.length() - (CLOSE.length() - 1));
                        break;
                    }
                }
            }
            carry.setLength(0);
            if (idx < buf.length()) {
                carry.append(buf, idx, buf.length());
            }
            return out.length() > 0 ? out.toString() : null;
        }

        /** 流结束时调用，输出缓冲中残留的正式回答内容；若仍处于 {@code <think>} 块内则丢弃。 */
        String flush() {
            String remaining = inThink ? null : carry.toString();
            carry.setLength(0);
            return (remaining == null || remaining.isEmpty()) ? null : remaining;
        }
    }
}
