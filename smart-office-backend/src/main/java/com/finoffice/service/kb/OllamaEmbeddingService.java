package com.finoffice.service.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ollama Embedding 服务：调用本地 Ollama /api/embeddings 将文本转为向量。
 * 使用 nomic-embed-text 模型（免费、轻量、中文兼容）。
 * 失败时记日志并抛出异常，由上层 Router 统一捕获回落。
 */
@Slf4j
@Service
public class OllamaEmbeddingService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${finpilot.rag.ollama-base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${finpilot.rag.embedding-model:nomic-embed-text}")
    private String embeddingModel;

    public OllamaEmbeddingService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 将单段文本转为向量。
     * @return 向量数组，维数取决于模型（nomic-embed-text 为 768 维）
     */
    public float[] embed(String text) {
        try {
            Map<String, Object> body = Map.of("model", embeddingModel, "prompt", text);
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/embeddings"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Ollama Embedding 返回非 200: " + response.statusCode()
                        + " body=" + response.body().substring(0, Math.min(response.body().length(), 200)));
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embeddingNode = root.get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new RuntimeException("Ollama Embedding 返回格式异常，缺少 embedding 数组");
            }

            float[] vec = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vec[i] = embeddingNode.get(i).floatValue();
            }
            return vec;
        } catch (Exception e) {
            log.error("Ollama Embedding 调用失败: {}", e.getMessage());
            throw new RuntimeException("Ollama Embedding 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量嵌入（一次请求一条，Ollama 不支持批量）。
     * 单条失败不影响其他条，失败项嵌入记为 null。
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> result = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text == null || text.isBlank()) {
                result.add(null);
                continue;
            }
            try {
                result.add(embed(text));
            } catch (Exception e) {
                log.error("批量嵌入第 {}/{} 条失败（跳过继续）: {}", i + 1, texts.size(), e.getMessage());
                result.add(null);
            }
        }
        return result;
    }
}
