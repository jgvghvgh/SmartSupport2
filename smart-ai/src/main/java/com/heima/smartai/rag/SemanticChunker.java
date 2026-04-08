package com.heima.smartai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义切分器
 *
 * 切分策略：
 * 1. 语义优先：先按段落/句子切分，保护语义完整性
 * 2. 重叠切分兜底：滑动窗口 + 重叠区域，确保边界内容不丢失
 * 3. 父子块架构：父块保留完整上下文，子块用于精确检索
 * 4. 元数据标记：记录来源段落、位置信息，支持回溯
 */
@Slf4j
@Component
public class SemanticChunker {

    // 段落分隔符
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\n\\s*\n|\r\n\\s*\r\n");
    // 句子分隔符（包括中文标点）
    private static final Pattern SENTENCE_PATTERN = Pattern.compile(
            "[。！？.!?；;]\s*|(?<=[，,])\\s*(?=[A-Z\u4e00-\u9fa5（\"「])"
    );

    // 子块目标大小（字符数）
    private static final int CHUNK_SIZE = 400;
    // 重叠区域大小（字符数）
    private static final int OVERLAP_SIZE = 80;
    // 单段落最大长度，超过才进一步切分
    private static final int PARAGRAPH_MAX_LENGTH = 600;

    /**
     * 切分文档
     *
     * @param document 原始文档
     * @return 切分后的文本段落列表
     */
    public List<TextSegment> chunk(Document document) {
        String text = document.text();
        return chunkText(text);
    }

    /**
     * 切分文本
     */
    public List<TextSegment> chunkText(String text) {
        List<TextSegment> result = new ArrayList<>();

        // 1. 按段落分割
        String[] paragraphs = PARAGRAPH_PATTERN.split(text);
        int paragraphIndex = 0;

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            paragraphIndex++;

            // 2. 判断段落长度，决定切分策略
            if (para.length() <= PARAGRAPH_MAX_LENGTH) {
                // 短段落：作为一个整体块
                if (para.length() > CHUNK_SIZE) {

                    result.addAll(slidingWindowChunk(para, paragraphIndex, result.size()));
                } else {
                    result.add(createSegment(para, paragraphIndex, 0, "normal"));
                }
            } else {
                // 长段落：语义切分 + 重叠兜底
                result.addAll(semanticChunkWithOverlap(para, paragraphIndex, result.size()));
            }
        }

        log.info("文档切分完成，段落数={}, 切分块数={}", paragraphIndex, result.size());
        return result;
    }

    /**
     * 语义切分 + 重叠兜底
     * 策略：先按句子切分，然后合并到目标大小，相邻块有重叠区域
     */
    private List<TextSegment> semanticChunkWithOverlap(String paragraph, int paragraphIndex, int globalIndex) {
        List<TextSegment> result = new ArrayList<>();

        // 按句子分割
        String[] sentences = SENTENCE_PATTERN.split(paragraph);
        if (sentences.length <= 1) {
            // 无法按句子分割，使用滑动窗口
            return slidingWindowChunk(paragraph, paragraphIndex, globalIndex);
        }

        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;
        String previousOverlap = ""; // 记录前一个块的重点内容

        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i].trim();
            if (sentence.isEmpty()) continue;

            // 如果加上这个句子会超过目标大小，先保存当前的
            if (currentChunk.length() + sentence.length() > CHUNK_SIZE && currentChunk.length() > 0) {
                // 保存当前块（带重叠前缀）
                String chunkText = previousOverlap + currentChunk.toString();
                result.add(createSegment(chunkText, paragraphIndex, chunkIndex, "semantic"));

                // 重叠区域：保留上一个块的最后部分
                previousOverlap = extractOverlapSuffix(currentChunk.toString(), OVERLAP_SIZE);

                // 重置
                currentChunk = new StringBuilder();
                chunkIndex++;
            }

            // 添加句子
            if (currentChunk.length() > 0) {
                currentChunk.append("。");
            }
            currentChunk.append(sentence);

            // 如果单个句子就超长，使用滑动窗口
            if (sentence.length() > CHUNK_SIZE) {
                result.addAll(slidingWindowChunk(sentence, paragraphIndex, globalIndex + result.size()));
                currentChunk = new StringBuilder();
                previousOverlap = "";
            }
        }

        // 处理最后一个块
        if (currentChunk.length() > 0) {
            String chunkText = previousOverlap + currentChunk.toString();
            result.add(createSegment(chunkText, paragraphIndex, chunkIndex, "semantic"));
        }

        return result;
    }

    /**
     * 滑动窗口切分（兜底方案）
     * 确保边界内容不会因切分而丢失
     */
    private List<TextSegment> slidingWindowChunk(String text, int paragraphIndex, int startGlobalIndex) {
        List<TextSegment> result = new ArrayList<>();

        if (text.length() <= CHUNK_SIZE) {
            result.add(createSegment(text, paragraphIndex, 0, "sliding"));
            return result;
        }

        int index = 0;
        int position = 0;

        while (position < text.length()) {
            int end = Math.min(position + CHUNK_SIZE, text.length());

            // 如果不是最后一块，尽量在句子边界结束
            if (end < text.length()) {
                end = findSentenceBoundary(text, end);
            }

            String chunk = text.substring(position, end);

            // 如果有重叠且不是第一块，添加前缀
            if (position > 0 && OVERLAP_SIZE > 0) {
                String prefix = extractOverlapSuffix(
                        text.substring(Math.max(0, position - OVERLAP_SIZE), position),
                        OVERLAP_SIZE / 2
                );
                chunk = prefix + chunk;
            }

            result.add(createSegment(chunk, paragraphIndex, index, "sliding"));

            // 滑动步长：块大小 - 重叠大小
            int step = CHUNK_SIZE - OVERLAP_SIZE;
            position += step > 0 ? step : CHUNK_SIZE / 2;
            index++;
        }

        return result;
    }

    /**
     * 在给定位置附近查找句子边界
     */
    private int findSentenceBoundary(String text, int position) {
        // 往后找最近的句子结束符
        for (int i = position; i < Math.min(position + 100, text.length()); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }
        // 往后找逗号或分号
        for (int i = position; i < Math.min(position + 50, text.length()); i++) {
            char c = text.charAt(i);
            if (c == '，' || c == ',' || c == '；' || c == ';') {
                return i + 1;
            }
        }
        // 找不到就返回原始位置
        return position;
    }

    /**
     * 提取字符串末尾的关键部分作为重叠区
     */
    private String extractOverlapSuffix(String text, int size) {
        if (text == null || text.isEmpty()) return "";
        int start = Math.max(0, text.length() - size);
        return "... " + text.substring(start);
    }

    /**
     * 创建 TextSegment，带元数据
     */
    private TextSegment createSegment(String text, int paragraphIndex, int chunkIndex, String chunkType) {
        dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
        metadata.put("paragraphIndex", String.valueOf(paragraphIndex));
        metadata.put("chunkIndex", String.valueOf(chunkIndex));
        metadata.put("chunkType", chunkType);
        metadata.put("charCount", String.valueOf(text.length()));

        return new TextSegment(text, metadata);
    }

    // ========== Getter 用于配置 ==========

    public int getChunkSize() {
        return CHUNK_SIZE;
    }

    public int getOverlapSize() {
        return OVERLAP_SIZE;
    }
}
