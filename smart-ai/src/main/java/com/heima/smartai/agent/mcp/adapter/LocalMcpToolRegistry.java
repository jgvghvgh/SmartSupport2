package com.heima.smartai.agent.mcp.adapter;

import com.heima.smartai.agent.core.SimpleTool;
import com.heima.smartai.agent.core.ToolFactory;
import com.heima.smartai.agent.mcp.types.McpTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 本地工具注册为MCP Server
 * 将SmartSupport的本地工具暴露给外部MCP Client调用
 */
@Component
@RequiredArgsConstructor
public class LocalMcpToolRegistry {

    private final ToolFactory toolFactory;

    /**
     * 获取所有本地工具的MCP格式
     */
    public List<McpTool> getLocalTools() {
        return toolFactory.getAllTools().stream()
                .map(this::convertToMcpTool)
                .collect(Collectors.toList());
    }

    /**
     * 调用本地工具
     */
    public McpTool.CallToolResult callLocalTool(String toolName, Map<String, Object> args) {
        SimpleTool tool = toolFactory.getTool(toolName);
        if (tool == null) {
            return new McpTool.CallToolResult(List.of(
                    McpTool.CallToolResult.ContentItem.text("未知工具: " + toolName)
            ));
        }

        try {
            SimpleTool.ToolResult result = tool.execute(args);
            if (result.success()) {
                return new McpTool.CallToolResult(List.of(
                        McpTool.CallToolResult.ContentItem.text(result.output())
                ));
            } else {
                return new McpTool.CallToolResult(List.of(
                        McpTool.CallToolResult.ContentItem.text("工具执行失败: " + result.error())
                ));
            }
        } catch (Exception e) {
            return new McpTool.CallToolResult(List.of(
                    McpTool.CallToolResult.ContentItem.text("工具执行异常: " + e.getMessage())
            ));
        }
    }

    /**
     * 将SimpleTool转换为McpTool
     */
    private McpTool convertToMcpTool(SimpleTool tool) {
        Map<String, McpTool.PropertySchema> schema = parseSchemaFromDescription(tool.description());
        return new McpTool(tool.name(), tool.description(), schema);
    }

    /**
     * 从description简单解析schema（简化实现）
     */
    private Map<String, McpTool.PropertySchema> parseSchemaFromDescription(String description) {
        // 简化：默认所有参数都是可选的string类型
        // 实际应该让SimpleTool提供更规范的schema
        return Map.of();
    }
}
