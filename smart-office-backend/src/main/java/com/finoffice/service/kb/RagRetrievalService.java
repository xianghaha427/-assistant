package com.finoffice.service.kb;

import com.finoffice.entity.KbDocument;
import com.finoffice.mapper.KbDocumentMapper;
import com.finoffice.service.kb.VectorIndex.ChunkEntry;
import com.finoffice.service.kb.VectorIndex.SearchResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * RAG 检索服务：编排文档加载 → 分块 → Embedding → 索引 → 检索的全流程。
 * 首次查询时懒加载构建索引（后续查询直接使用缓存索引）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final DocumentChunker chunker;
    private final OllamaEmbeddingService embeddingService;
    private final VectorIndex vectorIndex;
    private final KbDocumentMapper kbDocumentMapper;

    private final AtomicBoolean indexBuilt = new AtomicBoolean(false);

    @Value("${finpilot.rag.docs-path:knowledge-docs}")
    private String docsPath;

    @Value("${finpilot.rag.top-k:4}")
    private int topK;

    /** 检索上下文（查询命中块 + 来源引用） */
    public record RetrievalContext(String context, List<String> sources) {}

    /**
     * 检索与查询最相关的文档片段。
     * 首次调用时自动构建索引，后续调用直接查询。
     */
    public RetrievalContext search(String query) {
        ensureIndexBuilt();
        if (vectorIndex.isEmpty()) {
            return new RetrievalContext("", List.of());
        }

        float[] queryVec = embeddingService.embed(query);
        List<SearchResult> results = vectorIndex.search(queryVec, topK);

        if (results.isEmpty()) {
            return new RetrievalContext("", List.of());
        }

        StringBuilder context = new StringBuilder();
        List<String> sources = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            context.append("【资料片段 ").append(i + 1).append("】")
                    .append(r.docName() != null && !r.docName().isBlank()
                            ? "（来源：" + r.docName() + "）" : "")
                    .append("\n").append(r.chunk()).append("\n\n");
            if (r.docName() != null && !r.docName().isBlank()
                    && !sources.contains(r.docName())) {
                sources.add(r.docName());
            }
        }

        log.debug("RAG 检索命中 {} 条", results.size());
        return new RetrievalContext(context.toString(), sources);
    }

    /**
     * 强制重建索引（管理员修改文档后调用）。
     */
    public synchronized void rebuildIndex() {
        indexBuilt.set(false);
        try {
            buildIndex();
            indexBuilt.set(true);
        } catch (Exception e) {
            log.error("重建索引失败: {}", e.getMessage(), e);
            throw new RuntimeException("重建索引失败: " + e.getMessage(), e);
        }
    }

    /** 确保索引已构建（懒加载） */
    private synchronized void ensureIndexBuilt() {
        if (indexBuilt.get()) return;
        try {
            buildIndex();
            indexBuilt.set(true);
        } catch (Exception e) {
            log.error("构建 RAG 索引失败: {}", e.getMessage(), e);
            // 不抛异常，后续查询会返回空上下文 → 纯 LLM 模式
        }
    }

    /** 构建索引：加载文档 → 分块 → 嵌入 → 存入向量索引 */
    private void buildIndex() {
        // 1. 优先从后台上传的文档加载，确保自研知识库回答的是用户上传内容。
        List<DocEntry> docs = loadFromDatabase();
        if (docs.isEmpty()) {
            // 2. 备选：保留演示目录文档，避免未上传时原演示能力失效。
            docs = loadFromFilesystem();
        }
        if (docs.isEmpty()) {
            log.warn("未找到任何知识库文档，RAG 索引为空（将使用纯 LLM 模式）");
            return;
        }

        log.info("开始构建 RAG 索引：{} 篇文档", docs.size());

        // 3. 分块
        List<ChunkEntry> allChunks = new ArrayList<>();
        for (DocEntry doc : docs) {
            List<String> chunks = chunker.chunk(doc.content);
            for (String chunk : chunks) {
                allChunks.add(new ChunkEntry(chunk, null, doc.name));
            }
        }
        log.info("分块完成：{} 个文本块", allChunks.size());

        // 4. 批量嵌入（分批，每批 20 条，避免 Ollama 过载）
        int batchSize = 20;
        int successCount = 0;
        for (int i = 0; i < allChunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allChunks.size());
            List<ChunkEntry> batch = allChunks.subList(i, end);
            List<String> texts = batch.stream().map(ChunkEntry::chunk).collect(Collectors.toList());

            try {
                List<float[]> vecs = embeddingService.embedBatch(texts);
                for (int j = 0; j < batch.size(); j++) {
                    ChunkEntry entry = batch.get(j);
                    batch.set(j, new ChunkEntry(entry.chunk(), vecs.get(j), entry.docName()));
                }
                successCount += batch.size();
                log.debug("嵌入进度：{}/{}", successCount, allChunks.size());
            } catch (Exception e) {
                log.warn("批量嵌入第 {}-{} 条失败，跳过该批: {}", i + 1, end, e.getMessage());
            }
            // 批次间短暂休眠，避免打爆 Ollama
            if (end < allChunks.size()) {
                try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }

        // 5. 重建索引
        List<ChunkEntry> validChunks = allChunks.stream()
                .filter(e -> e.embedding() != null)
                .collect(Collectors.toList());
        vectorIndex.rebuild(validChunks);
        log.info("RAG 索引构建完成：{}/{} 个块成功嵌入", validChunks.size(), allChunks.size());
    }

    /** 从 knowledge-docs 目录加载 .md 文件 */
    private List<DocEntry> loadFromFilesystem() {
        List<DocEntry> docs = new ArrayList<>();
        try {
            Path dir = Paths.get(docsPath);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                log.info("知识库目录不存在: {}", docsPath);
                return docs;
            }
            // 遍历 .md 文件，排除 README 和子目录
            try (var stream = Files.list(dir)) {
                stream.filter(p -> {
                    if (Files.isDirectory(p)) return false;
                    String name = p.getFileName().toString();
                    return name.endsWith(".md") && !name.equals("README.md");
                }).forEach(p -> {
                    try {
                        String content = Files.readString(p, StandardCharsets.UTF_8);
                        String name = p.getFileName().toString().replace(".md", "");
                        if (content != null && !content.isBlank()) {
                            docs.add(new DocEntry(name, content));
                        }
                    } catch (IOException e) {
                        log.warn("读取文档失败: {} → {}", p.getFileName(), e.getMessage());
                    }
                });
            }
        } catch (IOException e) {
            log.warn("遍历知识库目录失败: {}", e.getMessage());
        }
        return docs;
    }

    /** 从数据库 kb_document 表加载文档（备选） */
    private List<DocEntry> loadFromDatabase() {
        List<DocEntry> docs = new ArrayList<>();
        try {
            List<KbDocument> list = kbDocumentMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbDocument>()
                            .in(KbDocument::getStatus, List.of("uploaded", "completed", "local_only"))
                            .isNotNull(KbDocument::getContent)
                            .ne(KbDocument::getContent, ""));
            for (KbDocument kb : list) {
                if (kb.getContent() != null && !kb.getContent().isBlank()) {
                    docs.add(new DocEntry(kb.getName(), kb.getContent()));
                }
            }
        } catch (Exception e) {
            log.warn("从数据库加载文档失败: {}", e.getMessage());
        }
        return docs;
    }

    /** 文档条目 */
    private record DocEntry(String name, String content) {}
}
