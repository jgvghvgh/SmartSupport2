package com.heima.smartai.agent.core;

import java.util.Map;

/**
 * 简单的工具接口
 */
public interface SimpleTool {
    String name();
    String description();
    ToolResult execute(Map<String, Object> params);

    /**
     * 返回工具的 JSON Schema 参数定义
     * 用于 LLM Function Calling
     * @return JSON Schema 格式的参数定义，默认返回空对象
     */
    default Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", java.util.List.of()
        );
    }

    record ToolResult(boolean success, String output, String error) {
        public static ToolResult ok(String output) {
            return new ToolResult(true, output, null);
        }
        public static ToolResult fail(String error) {
            return new ToolResult(false, null, error);
        }
    }
}
