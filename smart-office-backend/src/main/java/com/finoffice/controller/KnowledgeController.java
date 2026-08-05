package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.R;
import com.finoffice.entity.KbDocument;
import com.finoffice.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 知识库接口
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 上传文档（支持文本直接提交或文件上传）
     */
    @PostMapping("/documents")
    public R<KbDocument> uploadDocument(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        Long userId = StpUtil.getLoginIdAsLong();
        String fileType = "text";
        String docContent = content;
        String docName = name;

        // 文件上传模式
        if (file != null && !file.isEmpty()) {
            try {
                docContent = new String(file.getBytes(), StandardCharsets.UTF_8);
                docName = (docName != null && !docName.isBlank()) ? docName : file.getOriginalFilename();
                String originalName = file.getOriginalFilename();
                if (originalName != null) {
                    if (originalName.endsWith(".pdf")) {
                        fileType = "pdf";
                    } else if (originalName.endsWith(".docx") || originalName.endsWith(".doc")) {
                        fileType = "docx";
                    }
                }
            } catch (IOException e) {
                return R.error(500, "文件读取失败");
            }
        }

        KbDocument doc = knowledgeService.uploadDocument(userId, docName, docContent, fileType);
        return R.ok(doc);
    }

    /**
     * 文档列表
     */
    @GetMapping("/documents")
    public R<List<KbDocument>> listDocuments() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(knowledgeService.listDocuments(userId));
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/documents/{id}")
    public R<Void> deleteDocument(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        knowledgeService.deleteDocument(userId, id);
        return R.ok();
    }

    /**
     * 知识库问答
     */
    @PostMapping("/search")
    public R<Map<String, String>> search(@RequestBody Map<String, String> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        String query = body.get("query");
        String answer = knowledgeService.searchInKnowledge(userId, query);
        return R.ok(Map.of("answer", answer, "query", query));
    }
}
