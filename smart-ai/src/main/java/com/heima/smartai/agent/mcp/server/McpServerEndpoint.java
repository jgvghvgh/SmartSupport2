package com.heima.smartai.agent.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.smartai.agent.mcp.adapter.LocalMcpToolRegistry;
import com.heima.smartai.agent.mcp.types.JsonRpcMessage;
import com.heima.smartai.agent.mcp.types.McpConstants;
import com.heima.smartai.agent.mcp.types.McpTool;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Server HTTP端点
 * 处理 tools/list、tools/call、initialize 等JSON-RPC请求
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpServerEndpoint {
    private final LocalMcpToolRegistry toolRegistry;
    private final SseEmitterManager sseManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // 客户端连接信息
    private final Map<String, ClientInfo> clientInfos = new ConcurrentHashMap<>();
    private record ClientInfo(String clientId, String protocolVersion) {}
    /**
     * SSE连接端点 - 客户端通过此获取服务端推送
     */
    @GetMapping("/sse")
    public SseEmitter sse(HttpServletRequest request) {
        String clientId = UUID.randomUUID().toString();
        SseEmitter emitter = sseManager.createEmitter(clientId);
        // 发送初始连接确认
        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/mcp/rpc"));
        } catch (Exception e) {
            log.warn("SSE初始消息发送失败", e);
        }

        return emitter;
    }
    /**
     * JSON-RPC入口
     */
    @PostMapping("/rpc")
    public Object handleRpc(@RequestBody Map<String, Object> request) {
        try {
            String method = (String) request.get("method");
            Object params = request.get("params");
            String id = request.get("id") != null ? request.get("id").toString() : null;

            log.debug("MCP RPC请求: method={}, id={}", method, id);

            return switch (method) {
                case McpConstants.METHOD_INITIALIZE -> handleInitialize(params, id);
                case McpConstants.METHOD_TOOLS_LIST -> handleListTools(id);
                case McpConstants.METHOD_TOOLS_CALL -> handleCallTool(params, id);
                default -> JsonRpcMessage.JsonRpcResponse.error(
                        McpConstants.ERROR_METHOD_NOT_FOUND, "Method not found: " + method, id);
            };

        } catch (Exception e) {
            log.error("MCP RPC处理异常", e);
            return JsonRpcMessage.JsonRpcResponse.error(
                    McpConstants.ERROR_INTERNAL_ERROR, e.getMessage(), null);
        }
    }

    /**
     * initialize - 协议握手
     */
    @SuppressWarnings("unchecked")
    private Object handleInitialize(Object params, String id) {
        Map<String, Object> p = (Map<String, Object>) params;
        String protocolVersion = (String) p.get("protocolVersion");
        Map<String, Object> clientInfo = (Map<String, Object>) p.get("clientInfo");
        String clientName = clientInfo != null ? (String) clientInfo.get("name") : "unknown";

        log.info("MCP Client连接: client={}, protocolVersion={}", clientName, protocolVersion);

        // 返回服务端能力
        Map<String, Object> result = Map.of(
                "protocolVersion", McpConstants.PROTOCOL_VERSION,
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of(
                        "name", "smart-support-mcp",
                        "version", "1.0.0"
                )
        );

        return JsonRpcMessage.JsonRpcResponse.ok(result, id);
    }

    /**
     * tools/list - 列出所有可用工具
     */
    private Object handleListTools(String id) {
        var tools = toolRegistry.getLocalTools();
        var result = new McpTool.ListToolsResult(tools);
        return JsonRpcMessage.JsonRpcResponse.ok(result, id);
    }

    /**
     * tools/call - 调用工具
     */
    @SuppressWarnings("unchecked")
    private Object handleCallTool(Object params, String id) {
        Map<String, Object> p = (Map<String, Object>) params;
        String toolName = (String) p.get("name");
        Map<String, Object> arguments = (Map<String, Object>) p.get("arguments");

        log.info("调用本地工具: name={}", toolName);

        McpTool.CallToolResult result = toolRegistry.callLocalTool(toolName, arguments);
        return JsonRpcMessage.JsonRpcResponse.ok(result, id);
    }
}
