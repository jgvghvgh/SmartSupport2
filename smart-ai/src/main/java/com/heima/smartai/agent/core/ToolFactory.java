package com.heima.smartai.agent.core;

import com.heima.smartai.agent.tools.*;
import com.heima.smartai.agent.mcp.client.McpClientManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
                directChatTool
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
            default -> null;
        };
    }

    /**
     * 生成工具定义的Prompt片段（包含本地 + 外部MCP）
     */
    public String generateToolDefinitions() {
        StringBuilder sb = new StringBuilder();
        sb.append("你可以使用以下工具：\n\n");

        // 本地工具
        for (SimpleTool tool : getLocalTools()) {
            sb.append("## ").append(tool.name()).append("\n");
            sb.append(tool.description()).append("\n\n");
        }

        // 外部MCP工具
        List<McpClientManager.ExternalMcpTool> externalTools = mcpClientManager.getAllExternalTools();
        if (!externalTools.isEmpty()) {
            sb.append("## 外部工具\n");
            for (McpClientManager.ExternalMcpTool tool : externalTools) {
                sb.append("- ").append(tool.fullName())
                        .append(": ").append(tool.description()).append("\n");
            }
            sb.append("\n外部工具格式: serverName::toolName，例如: filesystem::read_file\n\n");
        }

        return sb.toString();
    }
}
