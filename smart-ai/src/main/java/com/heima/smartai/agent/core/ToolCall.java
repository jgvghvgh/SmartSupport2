package com.heima.smartai.agent.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 结构化工具调用
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /** 调用的工具名称 */
    private String name;

    /** 调用ID（用于多工具调用场景） */
    private String callId;

    /** 工具参数 */
    private Map<String, Object> arguments;

    /** 生成此调用的 LLM 思考过程（可选） */
    private String thought;

//    /**
//     * 从旧格式迁移的工厂方法（兼容旧 actionParams 字符串解析）
//     * @deprecated 逐步废弃，使用 LLM 原生 tool calling 后不再需要
//     */
//    @Deprecated
//    public static ToolCall fromActionParams(String toolName, String actionParamsJson, String thought) {
//        Map<String, Object> args = parseArgs(actionParamsJson);
//        return ToolCall.builder()
//                .name(toolName)
//                .arguments(args)
//                .thought(thought)
//                .build();
//    }

    private static Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return com.alibaba.fastjson2.JSON.parseObject(json);
        } catch (Exception e) {
            return Map.of("query", json);
        }
    }
}
