package com.finoffice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 语音转写服务：调用 Faster-Whisper Python 微服务将会议录音转为文字。
 * 服务不可用时抛出异常，由上层 MeetingService 统一捕获降级（提示用户手动粘贴文本）。
 */
@Slf4j
@Service
public class AudioTranscriptionService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${finpilot.whisper.base-url:http://localhost:8766}")
    private String whisperBaseUrl;

    @Value("${finpilot.whisper.enabled:true}")
    private boolean enabled;

    public AudioTranscriptionService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 将音频文件字节流转写为文字。
     *
     * @param audioBytes 音频文件内容
     * @param filename   原始文件名（用于推断格式）
     * @return 转写后的文本
     * @throws RuntimeException 转写失败或服务不可用
     */
    public String transcribe(byte[] audioBytes, String filename) {
        if (!enabled) {
            throw new RuntimeException("语音转写服务未启用");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new RuntimeException("音频文件为空");
        }

        try {
            // 构建 multipart/form-data 请求
            String boundary = "FinPilotWhisperBoundary" + System.currentTimeMillis();
            String safeName = filename != null ? filename : "recording.wav";

            var bodyBuilder = MultipartBodyBuilder.newBuilder(boundary)
                    .addFile("file", safeName, audioBytes)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(whisperBaseUrl + "/transcribe"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(300)) // 长音频可能需要更久
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBuilder))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String detail = extractDetail(response.body());
                throw new RuntimeException("语音转写失败(" + response.statusCode() + "): " + detail);
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("text").asText();
            if (text == null || text.isBlank()) {
                throw new RuntimeException("语音转写结果为空，请检查音频质量");
            }
            log.info("语音转写完成：{} 字, 音频时长 {} 秒",
                    text.length(), root.path("duration_seconds").asDouble());
            return text;
        } catch (IOException e) {
            throw new RuntimeException("语音转写网络请求失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("语音转写被中断", e);
        }
    }

    /** 检查转写服务是否可达 */
    public boolean isAvailable() {
        if (!enabled) return false;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(whisperBaseUrl + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractDetail(String body) {
        try {
            return objectMapper.readTree(body).path("detail").asText("未知错误");
        } catch (Exception e) {
            return body.substring(0, Math.min(body.length(), 200));
        }
    }

    /** 简易 multipart body 构建器（避免引入额外依赖） */
    private static class MultipartBodyBuilder {
        private final String boundary;
        private byte[] body;

        private MultipartBodyBuilder(String boundary, byte[] body) {
            this.boundary = boundary;
            this.body = body;
        }

        static MultipartBodyBuilder newBuilder(String boundary) {
            return new MultipartBodyBuilder(boundary, null);
        }

        MultipartBodyBuilder addFile(String fieldName, String filename, byte[] content) {
            StringBuilder sb = new StringBuilder();
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Disposition: form-data; name=\"").append(fieldName)
                    .append("\"; filename=\"").append(filename).append("\"\r\n");
            sb.append("Content-Type: application/octet-stream\r\n\r\n");
            byte[] header = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);

            body = new byte[header.length + content.length + footer.length];
            System.arraycopy(header, 0, body, 0, header.length);
            System.arraycopy(content, 0, body, header.length, content.length);
            System.arraycopy(footer, 0, body, header.length + content.length, footer.length);
            return this;
        }

        byte[] build() {
            return body;
        }
    }
}
