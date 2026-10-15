package com.finoffice.service;

import com.finoffice.config.DingTalkConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dingtalk.enabled", havingValue = "true")
public class DingTalkServiceImpl implements DingTalkService {

    private final DingTalkConfig dingTalkConfig;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void sendText(String content) {
        String json = "{\"msgtype\":\"text\",\"text\":{\"content\":\"" + escapeJson(content) + "\"}}";
        doPost(json);
    }

    @Override
    public void sendMarkdown(String title, String markdown) {
        String json = "{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\"" + escapeJson(title) + "\",\"text\":\"" + escapeJson(markdown) + "\"}}";
        doPost(json);
    }

    @Override
    public void sendMeetingSummary(String meetingTitle, String summaryJson) {
        String markdown = "## " + meetingTitle + "\n\n";
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(summaryJson);
            String abstract_ = root.path("abstract").asText("");
            if (!abstract_.isEmpty()) markdown += abstract_ + "\n\n";
            var decisions = root.path("decisionPoints");
            if (decisions.isArray() && decisions.size() > 0) {
                markdown += "**会议决议：**\n";
                for (var d : decisions) markdown += "- " + d.asText() + "\n";
                markdown += "\n";
            }
            var items = root.path("actionItems");
            if (items.isArray() && items.size() > 0) {
                markdown += "**待办事项：**\n";
                for (var item : items) {
                    String c = item.path("content").asText("");
                    String a = item.path("assignee").asText("");
                    String d = item.path("deadline").asText("");
                    markdown += "- " + c + "（" + a + "，截止" + d + "）\n";
                }
            }
        } catch (Exception e) {
            markdown += summaryJson;
        }
        markdown += "\n> 由恒生智能办公助手自动生成，会议简报";
        sendMarkdown("会议简报 - " + meetingTitle, markdown);
    }

    private void doPost(String json) {
        try {
            if (dingTalkConfig.getWebhookUrl() == null || dingTalkConfig.getWebhookUrl().isBlank()) {
                throw new IllegalStateException("钉钉 Webhook 未配置");
            }
            String url = buildSignedUrl();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("[DingTalk] HTTP error: status={}, body={}", response.statusCode(), response.body());
                throw new IllegalStateException("钉钉接口 HTTP 请求失败：" + response.statusCode());
            }

            JsonNode result = objectMapper.readTree(response.body());
            int errorCode = result.path("errcode").asInt(0);
            if (errorCode != 0) {
                String errorMessage = result.path("errmsg").asText("未知错误");
                log.error("[DingTalk] business error: errcode={}, errmsg={}", errorCode, errorMessage);
                throw new IllegalStateException("钉钉发送失败：" + errorMessage + "（" + errorCode + "）");
            }
            log.info("[DingTalk] send success: {}", response.body());
        } catch (Exception e) {
            log.error("[DingTalk] send failed", e);
            if (e instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw new IllegalStateException("钉钉消息发送失败", e);
        }
    }

    private String buildSignedUrl() {
        String webhookUrl = dingTalkConfig.getWebhookUrl();
        String secret = dingTalkConfig.getSecret();
        if (secret == null || secret.isBlank()) {
            return webhookUrl;
        }
        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), "UTF-8");
            return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            log.error("[DingTalk] sign failed, using original url", e);
            return webhookUrl;
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
