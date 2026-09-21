package com.finoffice.service.kb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文档分块器：将长文档切分为语义连贯的文本块。
 * 策略：优先按段落（双换行）切分，小块合并至目标大小，块间保留重叠。
 * 目标块大小 800 字，最大 1200 字，块间重叠 100 字。
 */
@Slf4j
@Component
public class DocumentChunker {

    /** 段落分隔正则：双换行 / Markdown 标题前 / 列表项边界 */
    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile(
            "(\\n\\s*\\n|\\n(?=#{1,4}\\s)|\\n(?=\\d+[.、])\\s*)");

    /** 目标块大小（字符数） */
    private static final int TARGET_SIZE = 800;

    /** 最大块大小 */
    private static final int MAX_SIZE = 1200;

    /** 最小块大小（低于此大小的段落会合并到前一块） */
    private static final int MIN_PARAGRAPH = 100;

    /** 块间重叠字符数 */
    private static final int OVERLAP = 100;

    /**
     * 将文档内容切分为文本块列表。
     */
    public List<String> chunk(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        // Step 1: 按段落切分
        String[] rawParagraphs = PARAGRAPH_SPLIT.split(content);
        List<String> paragraphs = new ArrayList<>();
        for (String p : rawParagraphs) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }

        if (paragraphs.isEmpty()) {
            return List.of();
        }

        // Step 2: 合并小段落，构建目标大小的块
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String prevChunkEnd = "";

        for (int i = 0; i < paragraphs.size(); i++) {
            String para = paragraphs.get(i);

            // 如果当前段本身就超过最大，单独成块并截断
            if (para.length() > MAX_SIZE) {
                // 先保存当前积累的块
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    prevChunkEnd = tailText(current.toString(), OVERLAP);
                    current = new StringBuilder(prevChunkEnd);
                }
                // 长段落按句子切分
                chunks.addAll(splitLongParagraph(para));
                continue;
            }

            if (current.length() + para.length() + 1 > MAX_SIZE && current.length() >= TARGET_SIZE / 2) {
                // 当前块已满，封存
                String chunk = current.toString().trim();
                chunks.add(chunk);
                prevChunkEnd = tailText(chunk, OVERLAP);
                current = new StringBuilder(prevChunkEnd);
                current.append("\n\n").append(para);
            } else if (current.length() + para.length() + 1 > MAX_SIZE && current.length() < TARGET_SIZE) {
                // 接近满但还没到目标一半，加进来后封存
                current.append("\n\n").append(para);
                String chunk = current.toString().trim();
                chunks.add(chunk);
                prevChunkEnd = tailText(chunk, OVERLAP);
                current = new StringBuilder(prevChunkEnd);
            } else if (para.length() < MIN_PARAGRAPH && current.length() > 0) {
                // 小段落直接合并
                current.append("\n\n").append(para);
            } else {
                if (current.length() > 0 && !current.toString().endsWith(prevChunkEnd)) {
                    current.append("\n\n");
                }
                current.append(para);
            }
        }

        // 最后一块
        if (current.length() > 0) {
            String last = current.toString().trim();
            // 去重：只去掉完全由重叠部分构成的块；短文档也必须保留，否则上传的简短 md/txt 无法被检索。
            if (!last.equals(prevChunkEnd.trim())) {
                chunks.add(last);
            }
        }

        log.debug("文档分块完成：{} 个段落 → {} 个块", paragraphs.size(), chunks.size());
        return chunks;
    }

    /**
     * 长段落按句子（句号、分号、换行）进一步切分。
     */
    private List<String> splitLongParagraph(String para) {
        List<String> result = new ArrayList<>();
        String[] sentences = para.split("(?<=[。；;\\n])");
        StringBuilder buf = new StringBuilder();

        for (String s : sentences) {
            if (buf.length() + s.length() > MAX_SIZE && buf.length() > 0) {
                result.add(buf.toString().trim());
                String end = tailText(buf.toString(), OVERLAP);
                buf = new StringBuilder(end);
            }
            buf.append(s);
        }
        if (buf.length() > 0) {
            result.add(buf.toString().trim());
        }
        return result;
    }

    /** 取文本尾部指定长度的字符（用于块间重叠） */
    private String tailText(String text, int length) {
        if (text.length() <= length) return "";
        return text.substring(text.length() - length);
    }
}
