package com.heima.smartai.agent.mcp.types;

/**
 * MCP协议常量
 */
public final class McpConstants {

    private McpConstants() {}

    // JSON-RPC错误码
    public static final int ERROR_PARSE_ERROR = -32700;
    public static final int ERROR_INVALID_REQUEST = -32600;
    public static final int ERROR_METHOD_NOT_FOUND = -32601;
    public static final int ERROR_INVALID_PARAMS = -32602;
    public static final int ERROR_INTERNAL_ERROR = -32603;

    // MCP方法名
    public static final String METHOD_INITIALIZE = "initialize";
    public static final String METHOD_TOOLS_LIST = "tools/list";
    public static final String METHOD_TOOLS_CALL = "tools/call";
    public static final String METHOD_RESOURCES_LIST = "resources/list";
    public static final String METHOD_RESOURCES_READ = "resources/read";

    // SSE事件类型
    public static final String EVENT_MESSAGE = "message";
    public static final String EVENT_TOOLS_LIST_CHANGED = "tools/list_changed";
    public static final String EVENT_RESOURCES_CHANGED = "resources/changed";

    // 协议版本
    public static final String PROTOCOL_VERSION = "2024-11-05";
}
