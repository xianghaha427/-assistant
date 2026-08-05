package com.finoffice.service;

import com.finoffice.config.DeepSeekConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * DeepSeek 流式对话服务
 * 使用 JDK 原生 HttpClient 调用 DeepSeek /v1/chat/completions 的 SSE 流式接口，
 * 作为 Dify 不可用时的降级方案。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekService {

    private final DeepSeekConfig deepSeekConfig;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 流式对话
     *
     * @param systemPrompt 系统提示词（来自智能体配置）
     * @param userMessage  用户消息
     * @param onChunk      每收到一段内容时的回调
     * @param onComplete   流结束时的回调
     */
    public void streamChat(String systemPrompt, String userMessage,
                           Consumer<String> onChunk, Runnable onComplete) {
        String url = deepSeekConfig.getBaseUrl().replaceAll("/+$", "") + "/v1/chat/completions";

        // 构造请求体 JSON
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(new ChatRequest(
                    deepSeekConfig.getModel(),
                    systemPrompt,
                    userMessage
            ));
        } catch (Exception e) {
            throw new RuntimeException("构造 DeepSeek 请求体失败", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + deepSeekConfig.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("DeepSeek API 返回非 200 状态码: {}，响应: {}", response.statusCode(), body);
                throw new RuntimeException("DeepSeek API 调用失败，状态码: " + response.statusCode());
            }

            // 逐行解析 SSE 流
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            onComplete.run();
                            return;
                        }
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            JsonNode delta = node.path("choices").path(0).path("delta");
                            String content = delta.path("content").asText(null);
                            if (content != null && !content.isEmpty()) {
                                onChunk.accept(content);
                            }
                        } catch (Exception parseEx) {
                            log.warn("解析 DeepSeek SSE 数据失败: {}", data, parseEx);
                        }
                    }
                }
                // 流自然结束（未收到 [DONE] 也视为完成）
                onComplete.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("DeepSeek 流式请求被中断", e);
        } catch (Exception e) {
            throw new RuntimeException("DeepSeek 流式请求异常: " + e.getMessage(), e);
        }
    }

    /**
     * 请求体内部 DTO
     */
    private record ChatRequest(String model, Message[] messages) {
        ChatRequest(String model, String systemPrompt, String userMessage) {
            this(model, new Message[]{
                    new Message("system", systemPrompt),
                    new Message("user", userMessage)
            });
        }
    }

    private record Message(String role, String content) {}
}
