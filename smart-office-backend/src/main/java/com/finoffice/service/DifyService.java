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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Dify 流式对话服务
 * 使用 JDK 原生 HttpClient 调用 Dify Chat API 的 SSE 流式接口
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
        String url = difyBaseUrl.replaceAll("/+$", "") + "/v1/chat-messages";

        // 构造请求体 JSON
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(new DifyChatRequest(
                    query,
                    conversationId != null ? conversationId : "",
                    "user-" + userId
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
                                    onChunk.accept(answer);
                                }
                            } else if ("message_end".equals(event)) {
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
                onComplete.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Dify 流式请求被中断", e);
        } catch (Exception e) {
            throw new RuntimeException("Dify 流式请求异常: " + e.getMessage(), e);
        }
    }

    /**
     * Dify Chat API 请求体内部 DTO
     */
    private record DifyChatRequest(Object inputs, String query, String response_mode,
                                   String conversation_id, String user) {
        DifyChatRequest(String query, String conversationId, String user) {
            this(new java.util.HashMap<>(), query, "streaming", conversationId, user);
        }
    }
}
