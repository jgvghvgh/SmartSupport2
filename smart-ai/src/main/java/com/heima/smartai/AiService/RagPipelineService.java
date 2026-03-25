package com.heima.smartai.AiService;

import com.heima.smartai.Config.AiClient;
import com.heima.smartai.model.AiAnalysisResult;
import com.heima.smartai.model.Message;
import com.heima.smartai.rag.PromptBuilder;
import com.heima.smartai.rag.QueryRewriteService;
import com.heima.smartai.rag.RerankService;
import com.heima.smartai.rag.VectorRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 流程服务
 * 独立提供完整的 RAG 链路：query改写 -> 向量检索 -> 重排 -> prompt构建 -> AI回复
 */
@Service
public class RagPipelineService {

    @Autowired
    private QueryRewriteService queryRewriteService;

    @Autowired
    private VectorRetriever vectorRetriever;

    @Autowired
    private RerankService rerankService;

    @Autowired
    private AiClient aiClient;

    /**
     * 执行 RAG 流程
     */
    public AiAnalysisResult chat(String question) {
        return chat(question, null);
    }

    /**
     * 执行 RAG 流程（带图片识别结果）
     */
    public AiAnalysisResult chat(String question, String imageAnalysisResult) {
        // 1 Query Rewrite
        String rewriteQuery = queryRewriteService.rewrite(question);

        // 2 Vector Search (embedding + search)
        List<String> docs = vectorRetriever.search(rewriteQuery);

        // 3 Rerank
        List<String> topDocs = rerankService.rerank(rewriteQuery, docs);

        // 4 Build Prompt
        String prompt = PromptBuilder.build(question, topDocs, imageAnalysisResult);

        // 5 AI Chat
        Message msg = Message.ofUser(prompt);
        String aiText = aiClient.chat(List.of(msg));

        // 6 Parse Response
        return aiClient.parseResponse(aiText);
    }
}
