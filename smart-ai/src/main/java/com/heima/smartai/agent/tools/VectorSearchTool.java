package com.heima.smartai.agent.tools;

import com.heima.smartai.agent.core.SimpleTool;
import com.heima.smartai.rag.VectorRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识库检索工具
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
        return "当用户询问的问题需要从知识库中查找答案时使用。例如：产品使用问题、常见故障排除、操作步骤咨询等。参数：query(必填，用户问题), top_k(可选，返回数量，默认3)";
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
