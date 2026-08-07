package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.BizException;
import com.finoffice.common.OpLogger;
import com.finoffice.common.R;
import com.finoffice.service.PptRenderService;
import com.finoffice.service.PptService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PPT大纲生成接口（agent#4 + Apache POI 真实 .pptx 输出）
 */
@Slf4j
@RestController
@RequestMapping("/api/ppt")
@RequiredArgsConstructor
public class PptController {

    private static final MediaType PPTX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private final PptService pptService;

    @Data
    public static class PptReq {
        /** PPT 主题 */
        private String topic;
    }

    /** 生成 PPT：调 Dify workflow 获取大纲 JSON → POI 渲染 → 落记录，返回记录 id */
    @PostMapping("/generate")
    public R<Map<String, Object>> generate(@RequestBody PptReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        if (req.getTopic() == null || req.getTopic().isBlank()) {
            throw new BizException("PPT 主题不能为空");
        }
        PptRenderService.PptRecord record = pptService.generate(req.getTopic().trim(), userId);
        OpLogger.log(userId, "PPT", "PPT生成触发", "主题：" + req.getTopic().trim() + "，记录ID " + record.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", record.getId());
        data.put("title", record.getTitle());
        data.put("pages", record.getPages());
        data.put("source", record.getSource());
        return R.ok(data);
    }

    /** 下载已生成的 .pptx 文件 */
    @GetMapping("/download/{id}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long id) {
        PptRenderService.PptRecord record = pptService.get(id);
        if (record == null) {
            throw new BizException("PPT 记录不存在或已失效，请重新生成");
        }
        byte[] pptx = record.getPptx();
        String filename = URLEncoder.encode(record.getTitle() + ".pptx", StandardCharsets.UTF_8)
                .replace("+", "%20");

        StreamingResponseBody body = out -> {
            out.write(pptx);
            out.flush();
        };
        return ResponseEntity.ok()
                .contentType(PPTX_MEDIA_TYPE)
                .contentLength(pptx.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(body);
    }
}
