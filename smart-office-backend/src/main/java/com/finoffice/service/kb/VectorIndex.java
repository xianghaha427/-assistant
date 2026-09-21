package com.finoffice.service.kb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 内存向量索引：存储文档块及其 Embedding 向量，支持余弦相似度 Top-K 搜索。
 * 线程安全（读写锁），重启后需重建索引（由 RagRetrievalService 负责）。
 */
@Slf4j
@Component
public class VectorIndex {

    /** 已索引的块列表（保持插入顺序，与 embeddings 按索引对应） */
    private final List<String> chunks = new ArrayList<>();

    /** 嵌入向量列表（与 chunks 一一对应） */
    private final List<float[]> embeddings = new ArrayList<>();

    /** 块所属文档名（用于溯源引用） */
    private final List<String> docNames = new ArrayList<>();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 清空并重建索引。
     */
    public void rebuild(List<ChunkEntry> entries) {
        lock.writeLock().lock();
        try {
            chunks.clear();
            embeddings.clear();
            docNames.clear();
            for (ChunkEntry entry : entries) {
                if (entry.embedding == null || entry.embedding.length == 0) continue;
                chunks.add(entry.chunk);
                embeddings.add(entry.embedding);
                docNames.add(entry.docName != null ? entry.docName : "");
            }
            log.info("向量索引已重建：{} 个块", chunks.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 追加块到索引（用于增量添加）。
     */
    public void add(String chunk, float[] embedding, String docName) {
        if (embedding == null || embedding.length == 0) return;
        lock.writeLock().lock();
        try {
            chunks.add(chunk);
            embeddings.add(embedding);
            docNames.add(docName != null ? docName : "");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 余弦相似度 Top-K 搜索。
     *
     * @param queryVec 查询向量
     * @param topK     返回数量（<=0 时自动设为 4）
     * @return 按相似度降序排列的搜索结果
     */
    public List<SearchResult> search(float[] queryVec, int topK) {
        if (topK <= 0) topK = 4;
        lock.readLock().lock();
        try {
            if (embeddings.isEmpty()) {
                return List.of();
            }

            // 计算所有相似度
            PriorityQueue<SearchResult> pq = new PriorityQueue<>(
                    Comparator.comparingDouble(SearchResult::similarity)); // 小顶堆保留 top-K

            for (int i = 0; i < embeddings.size(); i++) {
                float[] emb = embeddings.get(i);
                if (emb == null) continue;
                double sim = cosineSimilarity(queryVec, emb);
                SearchResult r = new SearchResult(chunks.get(i), docNames.get(i), sim, i);

                if (pq.size() < topK) {
                    pq.offer(r);
                } else if (sim > pq.peek().similarity()) {
                    pq.poll();
                    pq.offer(r);
                }
            }

            // 转为降序列表
            List<SearchResult> results = new ArrayList<>(pq);
            results.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 索引块数 */
    public int size() {
        lock.readLock().lock();
        try {
            return chunks.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 是否为空 */
    public boolean isEmpty() {
        return size() == 0;
    }

    /** 余弦相似度 = dot(a,b) / (|a| * |b|) */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不匹配: " + a.length + " vs " + b.length);
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** 索引条目（用于 rebuild） */
    public record ChunkEntry(String chunk, float[] embedding, String docName) {}

    /** 搜索结果 */
    public record SearchResult(String chunk, String docName, double similarity, int index) {}
}
