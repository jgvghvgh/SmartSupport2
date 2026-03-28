package com.heima.smartai.agent.mcp.types;

/**
 * JSON-RPC消息类型基类
 */
public sealed interface JsonRpcMessage
        permits JsonRpcMessage.JsonRpcRequest, JsonRpcMessage.JsonRpcResponse, JsonRpcMessage.JsonRpcError, JsonRpcMessage.JsonRpcNotification {

    String JSONRPC_VERSION = "2.0";

    record JsonRpcRequest(
            String jsonrpc,
            String method,
            Object params,
            String id
    ) implements JsonRpcMessage {
        public static JsonRpcRequest create(String method, Object params, String id) {
            return new JsonRpcRequest(JSONRPC_VERSION, method, params, id);
        }
    }

    record JsonRpcResponse(
            String jsonrpc,
            Object result,
            String id
    ) implements JsonRpcMessage {
        public static JsonRpcResponse ok(Object result, String id) {
            return new JsonRpcResponse(JSONRPC_VERSION, result, id);
        }
        public static JsonRpcResponse error(int code, String message, String id) {
            return new JsonRpcResponse(JSONRPC_VERSION, new JsonRpcError(code, message), id);
        }
    }

    record JsonRpcError(int code, String message) implements JsonRpcMessage {}

    record JsonRpcNotification(
            String jsonrpc,
            String method,
            Object params
    ) implements JsonRpcMessage {
        public static JsonRpcNotification create(String method, Object params) {
            return new JsonRpcNotification(JSONRPC_VERSION, method, params);
        }
    }
}
