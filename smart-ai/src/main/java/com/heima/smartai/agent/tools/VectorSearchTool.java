package com.heima.smartai.agent.tools;

import com.heima.smartai.agent.core.SimpleTool;
import com.heima.smartai.rag.VectorRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识库检索工具
 * 使用向量相似度搜索从知识库中查找相关文档
 */
@Component
@RequiredArgsConstructor
public class VectorSearchTool implements SimpleTool {

    private final VectorRetriever vectorRetriever;

    @Override
    public String name() {
        return "vector_search";
    }

    @Override
    public String description() {
        return """
            从知识库向量数据库中搜索与用户问题最相关的文档内容。

            【使用场景】
            - 用户询问产品使用方法、操作步骤
            - 用户询问常见故障的解决办法
            - 用户询问某个功能如何配置
            - 用户询问技术规格、参数问题
            - 用户问题涉及"怎么"、"如何"、"在哪找"、"怎么设置"等

            【返回内容】
            - 返回与问题最相关的知识库文档片段
            - 通常返回 top_k 条最相似的内容
            - 内容包含操作步骤、配置方法、故障排除指南等

            【使用建议】
            - 这是处理技术问题、產品问题的主要工具
            - 优先使用用户问题中的核心关键词作为 query
            - 如果搜索结果不够满意，可以尝试用不同表述重新搜索
            """;
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "搜索查询词，应该是用户问题的核心关键词或完整问题，例如：'如何重置密码'、'产品参数配置方法'"
                        ),
                        "top_k", Map.of(
                                "type", "integer",
                                "description", "返回的最相关文档数量，默认3。如果需要更全面结果可设为5-10"
                        )
                ),
                "required", List.of("query")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String query = (String) params.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.fail("query参数不能为空");
        }

        Integer topK = params.containsKey("top_k") ? (Integer) params.get("top_k") : 3;

        List<String> docs = vectorRetriever.search(query);
        if (docs.size() > topK) {
            docs = docs.subList(0, topK);
        }

        String output = String.join("\n---\n", docs);
        return ToolResult.ok(output);
    }
}
