package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.BizException;
import com.finoffice.common.R;
import com.finoffice.entity.KbDocument;
import com.finoffice.mapper.KbDocumentMapper;
import com.finoffice.service.KnowledgeService;
import com.finoffice.service.NotificationService;
import com.finoffice.service.kb.DocumentTextExtractor;
import com.finoffice.service.kb.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 知识库接口
 * - 知识问答（自研RAG/Dify双通道）
 * - 文档管理（上传/列表/删除/分类/重建索引）
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final KbDocumentMapper kbDocumentMapper;
    private final RagRetrievalService ragRetrievalService;
    private final NotificationService notificationService;
    private final DocumentTextExtractor documentTextExtractor;

    @Value("${app.upload-path:./uploads}")
    private String uploadPath;

    /** 知识库问答（已有） */
    @PostMapping("/search")
    public R<Map<String, String>> search(@RequestBody Map<String, String> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        String query = body.get("query");
        KnowledgeService.KnowledgeAnswer result = knowledgeService.searchInKnowledge(userId, query);
        return R.ok(Map.of("answer", result.answer(), "source", result.source(), "query", query));
    }

    /** 文档列表 */
    @GetMapping("/documents")
    public R<List<Map<String, Object>>> listDocuments() {
        List<KbDocument> docs = kbDocumentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbDocument>()
                        .orderByDesc(KbDocument::getCreateTime));
        List<Map<String, Object>> result = docs.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId()); m.put("name", d.getName()); m.put("fileType", d.getFileType());
            m.put("category", d.getCategory()); m.put("wordCount", d.getWordCount());
            m.put("status", d.getStatus()); m.put("createTime", d.getCreateTime());
            return m;
        }).toList();
        return R.ok(result);
    }

    /** 上传文档 */
    @PostMapping("/documents")
    public R<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "未分类") String category) {
        if (file.isEmpty()) throw new BizException("文件为空");
        String name = file.getOriginalFilename();
        String ext = name != null && name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!Set.of("pdf", "md", "txt", "docx").contains(ext))
            throw new BizException("仅支持 pdf/md/txt/docx 格式");

        try {
            String content = documentTextExtractor.extract(file, ext);
            if (content == null || content.isBlank()) {
                throw new BizException("未能从文档中解析出有效文本");
            }
            Path dir = Paths.get(uploadPath, "knowledge");
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID() + "." + ext;
            Path storedPath = dir.resolve(storedName);
            file.transferTo(storedPath);

            KbDocument doc = new KbDocument();
            doc.setName(name); doc.setFileType(ext); doc.setCategory(category);
            doc.setFileUrl(storedPath.toString());
            doc.setContent(content); doc.setStatus("completed");
            doc.setWordCount(content.length()); doc.setCreateTime(LocalDateTime.now());
            doc.setUserId(StpUtil.getLoginIdAsLong());
            kbDocumentMapper.insert(doc);

            try { ragRetrievalService.rebuildIndex(); } catch (Exception e) { /* ignore */ }

            notificationService.send(StpUtil.getLoginIdAsLong(), "knowledge", "文档已入库",
                    "《" + name + "》已导入知识库", "/knowledge");

            return R.ok(Map.of("id", doc.getId(), "name", name, "status", "completed"));
        } catch (BizException e) { throw e;
        } catch (Exception e) { throw new BizException("上传失败: " + e.getMessage()); }
    }

    /** 删除文档 */
    @DeleteMapping("/documents/{id}")
    public R<Void> deleteDocument(@PathVariable Long id) {
        KbDocument doc = kbDocumentMapper.selectById(id);
        if (doc == null) throw new BizException("文档不存在");
        kbDocumentMapper.deleteById(id);
        try { ragRetrievalService.rebuildIndex(); } catch (Exception e) { /* ignore */ }
        return R.ok();
    }

    /** 重建 RAG 索引 */
    @PostMapping("/rebuild-index")
    public R<Map<String, String>> rebuildIndex() {
        ragRetrievalService.rebuildIndex();
        return R.ok(Map.of("status", "ok"));
    }
}
