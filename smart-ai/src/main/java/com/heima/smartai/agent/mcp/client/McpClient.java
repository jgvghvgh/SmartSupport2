package com.heima.smartai.agent.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.smartai.agent.mcp.types.JsonRpcMessage;
import com.heima.smartai.agent.mcp.types.McpConstants;
import com.heima.smartai.agent.mcp.types.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单个MCP Server的Client连接
 */
@Slf4j
@Component
public class McpClient {

    private final String serverName;
    private final String serverUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private List<McpTool> cachedTools;

    public McpClient(String serverName, String serverUrl) {
        this.serverName = serverName;
        this.serverUrl = serverUrl;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 初始化连接：发送initialize并获取工具列表
     */
    public void initialize() {
        if (initialized.get()) {
            return;
        }

        try {
            // 1. 发送initialize
            String id = UUID.randomUUID().toString();
            Map<String, Object> params = Map.of(
                    "protocolVersion", McpConstants.PROTOCOL_VERSION,
                    "capabilities", Map.of("tools", Map.of()),
                    "clientInfo", Map.of("name", "smart-support", "version", "1.0.0")
            );

            JsonRpcMessage.JsonRpcRequest request = JsonRpcMessage.JsonRpcRequest.create(
                    McpConstants.METHOD_INITIALIZE, params, id);

            sendRequest(request);

            // 2. 获取工具列表
            cachedTools = listTools();
            initialized.set(true);

            log.info("MCP Client初始化成功, server={}, tools={}", serverName, cachedTools.size());

        } catch (Exception e) {
            log.error("MCP Client初始化失败, server={}", serverName, e);
            throw new RuntimeException("MCP Client初始化失败: " + serverName, e);
        }
    }

    /**
     * 获取工具列表
     */
    @SuppressWarnings("unchecked")
    public List<McpTool> listTools() {
        String id = UUID.randomUUID().toString();
        JsonRpcMessage.JsonRpcRequest request = JsonRpcMessage.JsonRpcRequest.create(
                McpConstants.METHOD_TOOLS_LIST, null, id);

        try {
            Map<String, Object> response = sendRequest(request);
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            if (result != null && result.get("tools") != null) {
                return parseTools((List<Map<String, Object>>) result.get("tools"));
            }
        } catch (Exception e) {
            log.error("获取工具列表失败, server={}", serverName, e);
        }
        return List.of();
    }

    /**
     * 调用工具
     */
    @SuppressWarnings("unchecked")
    public McpTool.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
        if (!initialized.get()) {
            initialize();
        }

        String id = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(
                "name", toolName,
                "arguments", arguments != null ? arguments : Map.of()
        );

        JsonRpcMessage.JsonRpcRequest request = JsonRpcMessage.JsonRpcRequest.create(
                McpConstants.METHOD_TOOLS_CALL, params, id);

        try {
            Map<String, Object> response = sendRequest(request);
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            if (result != null && result.get("content") != null) {
                List<Map<String, Object>> contentList = (List<Map<String, Object>>) result.get("content");
                List<McpTool.CallToolResult.ContentItem> items = contentList.stream()
                        .map(c -> new McpTool.CallToolResult.ContentItem(
                                (String) c.get("type"),
                                (String) c.get("text"),
                                (Map<String, Object>) c.get("data"),
                                (String) c.get("mimeType")
                        ))
                        .toList();
                return new McpTool.CallToolResult(items);
            }
        } catch (Exception e) {
            log.error("调用工具失败, server={}, tool={}", serverName, toolName, e);
            throw new RuntimeException("调用工具失败: " + toolName, e);
        }

        return new McpTool.CallToolResult(List.of(McpTool.CallToolResult.ContentItem.text("工具执行失败")));
    }

    public String getServerName() {
        return serverName;
    }

    public List<McpTool> getCachedTools() {
        return cachedTools;
    }

    private Map<String, Object> sendRequest(JsonRpcMessage.JsonRpcRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<JsonRpcMessage.JsonRpcRequest> entity = new HttpEntity<>(request, headers);
        String rpcUrl = serverUrl + "/rpc";

        return restTemplate.postForObject(rpcUrl, entity, Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<McpTool> parseTools(List<Map<String, Object>> toolsData) {
        return toolsData.stream()
                .map(toolData -> {
                    String name = (String) toolData.get("name");
                    String description = (String) toolData.get("description");
                    Map<String, Object> inputSchema = (Map<String, Object>) toolData.get("inputSchema");
                    return new McpTool(name, description, parseSchema(inputSchema));
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, McpTool.PropertySchema> parseSchema(Map<String, Object> inputSchema) {
        if (inputSchema == null) return Map.of();

        Map<String, McpTool.PropertySchema> result = new java.util.HashMap<>();
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        List<String> required = (List<String>) inputSchema.get("required");

        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                Map<String, Object> prop = (Map<String, Object>) entry.getValue();
                String type = (String) prop.get("type");
                String desc = (String) prop.get("description");
                boolean isRequired = required != null && required.contains(entry.getKey());
                result.put(entry.getKey(), new McpTool.PropertySchema(type, desc, isRequired));
            }
        }
        return result;
    }
}
