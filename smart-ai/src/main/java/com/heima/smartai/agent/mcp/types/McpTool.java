package com.heima.smartai.agent.mcp.types;

import java.util.List;
import java.util.Map;

/**
 * MCP工具定义
 */
public record McpTool(
        String name,
        String description,
        Map<String, PropertySchema> inputSchema
) {
    public record PropertySchema(
            String type,
            String description,
            Boolean required
    ) {}

    /**
     * MCP tools/list 返回结果
     */
    public record ListToolsResult(
            List<McpTool> tools
    ) {}

    /**
     * MCP tools/call 请求参数
     */
    public record CallToolRequest(
            String name,
            Map<String, Object> arguments
    ) {}

    /**
     * MCP tools/call 返回的内容项
     */
    public record CallToolResult(
            List<ContentItem> content
    ) {
        public record ContentItem(
                String type,
                String text,
                Map<String, Object> data,
                String mimeType
        ) {
            public static ContentItem text(String text) {
                return new ContentItem("text", text, null, null);
            }
        }
    }
}
