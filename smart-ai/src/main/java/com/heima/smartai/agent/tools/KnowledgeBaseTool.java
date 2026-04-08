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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识库问答工具（增强版）
 * 自动完成：查询改写 -> 向量搜索 -> 重排序 -> AI 整合回答
 */
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
        return """
            知识库问答工具，自动完成检索+回答的完整流程。

            【与 vector_search 的区别】
            - vector_search：只返回原始文档片段，不做总结
            - kb_search：自动检索 + AI 整合，直接给出完整回答

            【使用场景】
            - 用户需要一个完整的答案，而不是搜索文档
            - 问题需要综合多条知识库内容来回答
            - 需要 AI 基于知识库内容组织语言
            - 复杂问题需要"查资料 + 总结"一条龙服务

            【处理流程】
            1. 自动改写用户问题（Query Rewrite）提升检索效果
            2. 向量搜索查找相关文档
            3. 重排序精选最相关内容
            4. AI 整合生成最终答案

            【使用建议】
            - 如果只需要返回文档片段用 vector_search
            - 如果需要 AI 直接组织答案用 kb_search
            - 通常放在 vector_search 之后，当用户需要详细解答时调用
            """;
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of(
                                "type", "string",
                                "description", "用户的完整问题，会自动进行查询改写以提升检索效果"
                        ),
                        "imageAnalysisResult", Map.of(
                                "type", "string",
                                "description", "图片分析结果（可选），如果有图片分析内容需要一并传入，AI会结合图片信息综合回答"
                        )
                ),
                "required", List.of("question")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String question = (String) args.get("question");
        String imageAnalysisResult = (String) args.getOrDefault("imageAnalysisResult", "");

        try {
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
