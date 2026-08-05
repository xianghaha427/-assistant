package com.finoffice.service;

import com.finoffice.config.DingTalkConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

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
    public void sendMeetingSummary(String meetingTitle, String summaryText) {
        String markdown = "## " + meetingTitle + "\n\n" + summaryText;
        sendMarkdown("会议纪要 - " + meetingTitle, markdown);
    }

    private void doPost(String json) {
        try {
            String url = buildSignedUrl();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("[DingTalk] HTTP error: status={}, body={}", response.statusCode(), response.body());
            } else {
                log.info("[DingTalk] send success: {}", response.body());
            }
        } catch (Exception e) {
            log.error("[DingTalk] send failed", e);
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