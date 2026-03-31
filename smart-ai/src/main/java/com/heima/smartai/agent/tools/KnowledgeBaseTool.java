package com.heima.smartai.agent.tools;

import com.heima.smartai.Config.AiClient;
import com.heima.smartai.agent.core.SimpleTool;
import com.heima.smartai.model.AiAnalysisResult;
import com.heima.smartai.model.Message;
import com.heima.smartai.rag.PromptBuilder;
import com.heima.smartai.rag.QueryRewriteService;
import com.heima.smartai.rag.RerankService;
import com.heima.smartai.rag.VectorRetriever;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Property;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class KnowledgeBaseTool implements SimpleTool {

    @Autowired
    private QueryRewriteService queryRewriteService;

    @Autowired
    private VectorRetriever vectorRetriever;

    @Autowired
    private RerankService rerankService;

    @Autowired
    private AiClient aiClient;

    @Override
    public String name() {
        return "kb_search";
    }

    @Override
    public String description() {
        return "用于查询知识库，回答用户问题";
    }



    @Override
    public ToolResult execute(Map<String, Object> args) {

        String question = (String) args.get("question");
        String imageAnalysisResult = (String) args.getOrDefault("imageAnalysisResult", "");

        try {
            // ===== 原来的流程 그대로搬过来 =====

            // 1 Query Rewrite
            String rewriteQuery = queryRewriteService.rewrite(question);

            // 2 Vector Search
            List<String> docs = vectorRetriever.search(rewriteQuery);

            // 3 Rerank
            List<String> topDocs = rerankService.rerank(rewriteQuery, docs);

            // 4 Build Prompt
            String prompt = PromptBuilder.build(question, topDocs, imageAnalysisResult);

            // 5 AI Chat
            Message msg = Message.ofUser(prompt);
            String aiText = aiClient.chat(List.of(msg));

            // 6 Parse Response
            AiAnalysisResult result = aiClient.parseResponse(aiText);

            return ToolResult.ok(result.toString());

        } catch (Exception e) {
            log.error("知识库工具执行失败", e);
            return ToolResult.fail("知识库查询失败");
        }
    }
}