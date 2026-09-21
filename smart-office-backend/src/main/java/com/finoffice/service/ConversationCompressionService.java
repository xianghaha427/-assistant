package com.finoffice.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 长会话压缩服务（赛题 M2 P0）：
 * 当前不再接入外部模型做摘要，避免 Dify 之外的模型通道调用。
 */
@Service
public class ConversationCompressionService {

    /** 触发压缩的消息数阈值 */
    private static final int COMPRESS_THRESHOLD = 20;

    /** 保留最近 N 条消息不压缩 */
    private static final int KEEP_RECENT = 6;

    /**
     * 判断是否需要压缩。
     */
    public boolean shouldCompress(int messageCount) {
        return messageCount >= COMPRESS_THRESHOLD;
    }

    /**
     * 生成历史消息摘要。
     * @param recentMessages 最近的消息（JSON字符串列表，格式: [{"role":"user","content":"..."}, ...]）
     * @return 压缩后的摘要文本，失败返回 null
     */
    public String compress(List<String> recentMessages) {
        return null;
    }

    public int getThreshold() { return COMPRESS_THRESHOLD; }
    public int getKeepRecent() { return KEEP_RECENT; }
}
