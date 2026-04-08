package com.heima.smartai.agent.core;

import com.heima.smartai.agent.tools.*;
import com.heima.smartai.agent.mcp.client.McpClientManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具工厂 - 管理所有可用工具（本地 + 外部MCP）
 */
@Component
@RequiredArgsConstructor
public class ToolFactory {

    private final VectorSearchTool vectorSearchTool;
    private final TicketQueryTool ticketQueryTool;
    private final ImageRecognitionTool imageRecognitionTool;
    private final IntentClassifyTool intentClassifyTool;
    private final DirectChatTool directChatTool;
    private final KnowledgeBaseTool knowledgeBaseTool;
    private final McpClientManager mcpClientManager;

    /**
     * 获取所有本地工具
     */
    public List<SimpleTool> getLocalTools() {
        return List.of(
                vectorSearchTool,
                ticketQueryTool,
                imageRecognitionTool,
                intentClassifyTool,
                directChatTool,
                knowledgeBaseTool
        );
    }

    /**
     * 获取所有可用工具（包括外部MCP）
     */
    public List<SimpleTool> getAllTools() {
        return new ArrayList<>(getLocalTools());
    }

    /**
     * 获取本地工具
     */
    public SimpleTool getTool(String name) {
        return switch (name) {
            case "vector_search" -> vectorSearchTool;
            case "ticket_query" -> ticketQueryTool;
            case "image_recognition" -> imageRecognitionTool;
            case "intent_classify" -> intentClassifyTool;
            case "direct_chat" -> directChatTool;
            case "kb_search" -> knowledgeBaseTool;
            default -> null;
        };
    }

    //Tool Schema 生成（用于 LLM Function Calling）

    /**
     * 生成工具 Schema 列表
     * 用于 LLM Function Calling 模式
     *
     * 每个工具的 description 和 parameters 都从工具类自身获取
     */
    public ToolSchemas generateToolSchemas() {
        List<ToolSchemas.ToolSchema> schemas = new ArrayList<>();

        // 本地工具 - 从每个工具类获取 description 和 parameters
        for (SimpleTool tool : getLocalTools()) {
            schemas.add(buildSchema(tool.name(), tool.description(), tool.parameters()));
        }

        // 外部 MCP 工具
        List<McpClientManager.ExternalMcpTool> externalTools = mcpClientManager.getAllExternalTools();
        for (McpClientManager.ExternalMcpTool tool : externalTools) {
            schemas.add(buildSchema(
                    tool.fullName(),
                    tool.description(),
                    Map.of("type", "object", "properties", Map.of())
            ));
        }

        return ToolSchemas.builder().tools(schemas).build();
    }

    private ToolSchemas.ToolSchema buildSchema(String name, String description, Map<String, Object> parameters) {
        return ToolSchemas.ToolSchema.builder()
                .type("function")
                .function(ToolSchemas.ToolSchema.Function.builder()
                        .name(name)
                        .description(description)
                        .parameters(parameters)
                        .build())
                .build();
    }
}
