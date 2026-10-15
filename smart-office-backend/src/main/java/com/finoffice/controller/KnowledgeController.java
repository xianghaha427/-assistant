package com.finoffice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.finoffice.common.BizException;
import com.finoffice.common.R;
import com.finoffice.entity.KbDocument;
import com.finoffice.mapper.KbDocumentMapper;
import com.finoffice.service.KnowledgeService;
import com.finoffice.service.KnowledgePermissionService;
import com.finoffice.service.NotificationService;
import com.finoffice.service.kb.DocumentTextExtractor;
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
 * - 知识问答（Dify，默认模式）
 * - 本地文档问答（只检索当前用户有权限访问的平台上传文档，需配置模型端点）
 * - 文档管理（上传/列表/删除/分类/授权）
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final KbDocumentMapper kbDocumentMapper;
    private final NotificationService notificationService;
    private final DocumentTextExtractor documentTextExtractor;
    private final KnowledgePermissionService permissionService;

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

    /**
     * 本地文档问答（新增模式）：只检索当前用户有权限访问的平台上传文档。
     * 响应结构与 /search 完全一致，source 取值 local / local-empty / local-disabled / local-error。
     * 与 /search 并存、互不影响：前端按用户选择的模式决定调哪一个。
     */
    @PostMapping("/search-local")
    public R<Map<String, String>> searchLocal(@RequestBody Map<String, String> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        String query = body.get("query");
        KnowledgeService.KnowledgeAnswer result = knowledgeService.searchLocal(userId, query);
        return R.ok(Map.of("answer", result.answer(), "source", result.source(), "query", query));
    }

    /** 本地文档模式可用性；前端据此决定「本地文档」开关是否可点 */
    @GetMapping("/local-mode")
    public R<Map<String, Object>> localMode() {
        return R.ok(Map.of("enabled", knowledgeService.isLocalModeEnabled()));
    }

    /** 文档列表 */
    @GetMapping("/documents")
    public R<List<Map<String, Object>>> listDocuments() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<KbDocument> docs = permissionService.accessibleDocuments(userId);
        List<Map<String, Object>> result = docs.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId()); m.put("name", d.getName()); m.put("fileType", d.getFileType());
            m.put("category", d.getCategory()); m.put("wordCount", d.getWordCount());
            m.put("status", d.getStatus()); m.put("createTime", d.getCreateTime());
            m.put("deptId", d.getDeptId());
            m.put("canEdit", permissionService.canManage(d, userId, "EDIT"));
            m.put("canDelete", permissionService.canManage(d, userId, "DELETE"));
            m.put("canManageAcl", permissionService.canManageAcl(d, userId));
            return m;
        }).toList();
        return R.ok(result);
    }

    @GetMapping("/documents/{id}")
    public R<Map<String, Object>> getDocument(@PathVariable Long id) {
        KbDocument doc = kbDocumentMapper.selectById(id);
        Long userId = StpUtil.getLoginIdAsLong();
        permissionService.requireRead(doc, userId);
        return R.ok(documentView(doc, userId));
    }

    @PutMapping("/documents/{id}")
    public R<Map<String, Object>> updateDocument(@PathVariable Long id,
                                                   @RequestBody UpdateDocumentReq req) {
        KbDocument doc = kbDocumentMapper.selectById(id);
        Long userId = StpUtil.getLoginIdAsLong();
        permissionService.requireManage(doc, userId, "EDIT");
        if (req.getName() != null && !req.getName().isBlank()) doc.setName(req.getName().trim());
        if (req.getCategory() != null && !req.getCategory().isBlank()) doc.setCategory(req.getCategory().trim());
        if (req.getContent() != null) {
            if (req.getContent().isBlank()) throw new BizException("文档内容不能为空");
            doc.setContent(req.getContent());
            doc.setWordCount(req.getContent().length());
        }
        kbDocumentMapper.updateById(doc);
        return R.ok(documentView(doc, userId));
    }

    @lombok.Data
    public static class UpdateDocumentReq {
        private String name;
        private String category;
        private String content;
    }

    private Map<String, Object> documentView(KbDocument doc, Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", doc.getId()); result.put("name", doc.getName());
        result.put("fileType", doc.getFileType()); result.put("category", doc.getCategory());
        result.put("content", doc.getContent());
        result.put("wordCount", doc.getWordCount()); result.put("createTime", doc.getCreateTime());
        result.put("canEdit", permissionService.canManage(doc, userId, "EDIT"));
        result.put("canDelete", permissionService.canManage(doc, userId, "DELETE"));
        result.put("canManageAcl", permissionService.canManageAcl(doc, userId));
        return result;
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
            doc.setVisibility("private"); // 兼容旧表结构，实际访问控制完全由 ACL 决定
            doc.setFileUrl(storedPath.toString());
            doc.setContent(content); doc.setStatus("completed");
            doc.setWordCount(content.length()); doc.setCreateTime(LocalDateTime.now());
            doc.setUserId(StpUtil.getLoginIdAsLong());
            var currentUser = permissionService.currentUser();
            doc.setDeptId(currentUser.deptId());
            kbDocumentMapper.insert(doc);

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
        permissionService.requireManage(doc, StpUtil.getLoginIdAsLong(), "DELETE");
        kbDocumentMapper.deleteById(id);
        return R.ok();
    }

    @GetMapping("/documents/{id}/permissions")
    public R<List<KnowledgePermissionService.PermissionGrant>> listPermissions(@PathVariable Long id) {
        return R.ok(permissionService.listGrants(id, StpUtil.getLoginIdAsLong()));
    }

    @PutMapping("/documents/{id}/permissions")
    public R<Void> replacePermissions(@PathVariable Long id,
                                      @RequestBody List<KnowledgePermissionService.GrantRequest> requests) {
        permissionService.replaceGrants(id, requests, StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    @GetMapping("/permission-options")
    public R<Map<String, Object>> permissionOptions() {
        throw new BizException("请先选择具体文档");
    }

    @GetMapping("/documents/{id}/permission-options")
    public R<Map<String, Object>> documentPermissionOptions(@PathVariable Long id) {
        return R.ok(permissionService.grantOptions(id, StpUtil.getLoginIdAsLong()));
    }

}
