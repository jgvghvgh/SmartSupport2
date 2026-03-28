package com.heima.smartai.agent.mcp.client;

import com.heima.smartai.agent.core.SimpleTool;
import com.heima.smartai.agent.mcp.config.McpClientConfig;
import com.heima.smartai.agent.mcp.types.McpTool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Client管理器 - 管理多个MCP Server连接
 */
@Slf4j
@Component
public class McpClientManager {

    private final McpClientConfig clientConfig;
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();

    public McpClientManager(McpClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    @PostConstruct
    public void init() {
        if (clientConfig.getServers() == null) {
            log.info("未配置MCP Servers");
            return;
        }

        for (McpClientConfig.McpServerConfig server : clientConfig.getServers()) {
            if (!server.isEnabled()) {
                continue;
            }
            try {
                McpClient client = new McpClient(server.getName(), server.getUrl());
                client.initialize();
                clients.put(server.getName(), client);
                log.info("MCP Server连接成功: {}", server.getName());
            } catch (Exception e) {
                log.warn("MCP Server连接失败: {}, error={}", server.getName(), e.getMessage());
            }
        }
    }

    /**
     * 获取所有MCP Client的外部工具
     */
    public List<ExternalMcpTool> getAllExternalTools() {
        List<ExternalMcpTool> result = new ArrayList<>();
        for (Map.Entry<String, McpClient> entry : clients.entrySet()) {
            String serverName = entry.getKey();
            McpClient client = entry.getValue();
            for (var tool : client.getCachedTools()) {
                result.add(new ExternalMcpTool(serverName, tool.name(), tool.description()));
            }
        }
        return result;
    }

    /**
     * 调用外部MCP工具
     */
    public SimpleTool.ToolResult callExternalTool(String serverName, String toolName, Map<String, Object> args) {
        McpClient client = clients.get(serverName);
        if (client == null) {
            return SimpleTool.ToolResult.fail("未知MCP Server: " + serverName);
        }

        try {
            McpTool.CallToolResult result = client.callTool(toolName, args);
            String output = result.content().stream()
                    .map(c -> c.text() != null ? c.text() : "")
                    .reduce((a, b) -> a + b)
                    .orElse("");
            return SimpleTool.ToolResult.ok(output);
        } catch (Exception e) {
            log.error("调用外部工具失败, server={}, tool={}", serverName, toolName, e);
            return SimpleTool.ToolResult.fail("工具执行失败: " + e.getMessage());
        }
    }

    /**
     * 外部工具信息
     */
    public record ExternalMcpTool(String serverName, String toolName, String description) {
        public String fullName() {
            return serverName + "::" + toolName;
        }
    }
}
