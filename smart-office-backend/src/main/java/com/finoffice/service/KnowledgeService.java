package com.finoffice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finoffice.common.BizException;
import com.finoffice.entity.KbDocument;
import com.finoffice.mapper.KbDocumentMapper;
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

/**
 * 知识库服务
 * 支持文档上传、列表、删除，以及通过 Dify 进行知识问答
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KbDocumentMapper kbDocumentMapper;
    private final ObjectMapper objectMapper;

    @Value("${dify.base-url:http://localhost/v1}")
    private String difyBaseUrl;

    @Value("${dify.api-key:}")
    private String difyApiKey;

    @Value("${dify.enabled:true}")
    private boolean difyEnabled;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 上传文档：保存到数据库，并尝试上传到 Dify
     */
    public KbDocument uploadDocument(Long userId, String name, String content, String fileType) {
        if (name == null || name.isBlank()) {
            throw new BizException("文档名称不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BizException("文档内容不能为空");
        }

        KbDocument doc = new KbDocument();
        doc.setUserId(userId);
        doc.setName(name);
        doc.setFileType(fileType != null ? fileType : "text");
        doc.setContent(content);
        doc.setWordCount(content.length());
        doc.setStatus("pending");

        // 尝试上传到 Dify
        if (difyEnabled && difyApiKey != null && !difyApiKey.isBlank()) {
            try {
                String difyDocId = uploadToDify(name, content);
                doc.setDifyDocumentId(difyDocId);
                doc.setStatus("indexing");
            } catch (Exception e) {
                log.warn("上传文档到 Dify 失败，将仅本地存储: {}", e.getMessage());
                doc.setStatus("local_only");
            }
        } else {
            doc.setStatus("local_only");
        }

        kbDocumentMapper.insert(doc);
        return doc;
    }

    /**
     * 列出当前用户的文档
     */
    public List<KbDocument> listDocuments(Long userId) {
        return kbDocumentMapper.selectList(
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getUserId, userId)
                        .orderByDesc(KbDocument::getCreateTime));
    }

    /**
     * 删除文档（含归属校验 + Dify 侧删除）
     */
    public void deleteDocument(Long userId, Long docId) {
        KbDocument doc = kbDocumentMapper.selectById(docId);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        if (!doc.getUserId().equals(userId)) {
            throw new BizException("无权删除该文档");
        }

        // 尝试从 Dify 删除
        if (doc.getDifyDocumentId() != null && !doc.getDifyDocumentId().isBlank()) {
            try {
                deleteFromDify(doc.getDifyDocumentId());
            } catch (Exception e) {
                log.warn("从 Dify 删除文档失败: {}", e.getMessage());
            }
        }

        kbDocumentMapper.deleteById(docId);
    }

    /**
     * 知识库问答：通过 Dify Chat API
     */
    public String searchInKnowledge(Long userId, String query) {
        if (query == null || query.isBlank()) {
            throw new BizException("查询内容不能为空");
        }

        if (!difyEnabled || difyApiKey == null || difyApiKey.isBlank()) {
            return "知识库服务暂未接入 Dify，请先配置 dify.api-key 或上传文档到 Dify。";
        }

        try {
            return callDifyChat(userId, query);
        } catch (Exception e) {
            log.error("Dify 知识问答失败", e);
            throw new BizException("知识问答失败: " + e.getMessage());
        }
    }

    // ==================== Dify API 调用 ====================

    /**
     * 上传文档到 Dify Knowledge API
     * POST {base-url}/v1/datasets/{dataset_id}/document/create-by-text
     */
    private String uploadToDify(String name, String content) throws Exception {
        // 注意：这里使用 difyApiKey 作为 dataset-api-key
        // 实际使用时需要在 application.yml 中配置 dataset-id
        String datasetId = System.getProperty("dify.dataset-id", "default");
        String url = difyBaseUrl.replaceAll("/+$", "") + "/v1/datasets/" + datasetId + "/document/create-by-text";

        String requestBody = objectMapper.writeValueAsString(new DifyDocRequest(name, content));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + difyApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Dify API 返回状态码: " + response.statusCode());
        }

        JsonNode result = objectMapper.readTree(response.body());
        return result.path("document").path("id").asText(null);
    }

    /**
     * 从 Dify 删除文档
     * DELETE {base-url}/v1/datasets/{dataset_id}/documents/{document_id}
     */
    private void deleteFromDify(String documentId) throws Exception {
        String datasetId = System.getProperty("dify.dataset-id", "default");
        String url = difyBaseUrl.replaceAll("/+$", "") + "/v1/datasets/" + datasetId + "/documents/" + documentId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + difyApiKey)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 204 && response.statusCode() != 404) {
            log.warn("Dify 删除文档返回状态码: {}", response.statusCode());
        }
    }

    /**
     * 调用 Dify Chat API 进行知识问答
     */
    private String callDifyChat(Long userId, String query) throws Exception {
        String url = difyBaseUrl.replaceAll("/+$", "") + "/v1/chat-messages";

        String requestBody = objectMapper.writeValueAsString(new DifyChatRequest(query, "user-" + userId));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + difyApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Dify Chat API 返回状态码: " + response.statusCode());
        }

        JsonNode result = objectMapper.readTree(response.body());
        return result.path("answer").asText("暂无相关回答");
    }

    // ==================== 内部 DTO ====================

    private record DifyDocRequest(String name, String text, String indexing_technique, ProcessRule process_rule) {
        DifyDocRequest(String name, String text) {
            this(name, text, "high_quality", new ProcessRule("automatic"));
        }
    }

    private record ProcessRule(String mode) {}

    private record DifyChatRequest(Object inputs, String query, String response_mode, String user) {
        DifyChatRequest(String query, String user) {
            this(new java.util.HashMap<>(), query, "blocking", user);
        }
    }
}
