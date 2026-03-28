package com.heima.smartai.agent.core;

import java.util.Map;

/**
 * 简单的工具接口
 */
public interface SimpleTool {
    String name();
    String description();
    ToolResult execute(Map<String, Object> params);

    record ToolResult(boolean success, String output, String error) {
        public static ToolResult ok(String output) {
            return new ToolResult(true, output, null);
        }
        public static ToolResult fail(String error) {
            return new ToolResult(false, null, error);
        }
    }
}
