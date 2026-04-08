package com.heima.smartai.rag;

import com.heima.smartai.Config.AiClient;
import com.heima.smartai.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * HyDE (Hypothetical Document Embeddings) 服务
 * 使用LLM生成假设文档，然后用假设文档的embedding去做向量搜索
 * 这样可以提升检索的召回率和准确性
 */
@Slf4j
@Service
public class HydeService {

    @Autowired
    private AiClient aiClient;

    /**
     * 生成假设文档（Hypoythetical Document）
     * 使用LLM生成一个"理想答案"，然后用这个答案做向量搜索
     *
     * @param query 用户问题
     * @return 假设的知识库文档片段
     */
    public String generateHypotheticalDoc(String query) {
        String prompt = """
        请根据用户问题，生成一个假设的知识库文档片段。
        这个文档应该直接回答用户的问题，包含可能的关键信息和解答。

        用户问题：%s

        要求：
        1. 用第三人称叙述，像知识库文档风格
        2. 长度控制在200字以内
        3. 只输出文档内容，不要其他解释
        """.formatted(query);

        try {
            List<Message> messages = List.of(Message.ofUser(prompt));
            String hypoDoc = aiClient.chat(messages);
            String result = hypoDoc != null ? hypoDoc.trim() : query;
            log.debug("HyDE生成的假设文档: {}", result);
            return result;
        } catch (Exception e) {
            log.error("HyDE生成失败，使用原始query: {}", e.getMessage());
            return query;
        }
    }
}
