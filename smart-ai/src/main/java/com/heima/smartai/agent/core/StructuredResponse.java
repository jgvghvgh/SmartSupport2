package com.heima.smartai.agent.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 结构化ReAct响应
 * 替代纯文本格式，提供类型安全的解析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredResponse {

    /**
     * 响应类型
     */
    public enum ResponseType {
        /** 调用工具（新版 - 直接从 tool calling 获取） */
        TOOL_CALL,
        /** 执行工具（旧版 - 通过文本解析） */
        ACTION,
        /** 最终答案 */
        FINAL_ANSWER,
        /** 解析失败 */
        PARSE_ERROR
    }

    private ResponseType type;

    /** 思考过程 */
    private String thought;

    // ===== TOOL_CALL 类型字段 =====
    /** 工具名称（TOOL_CALL / ACTION 类型时） */
    private String toolName;

    /** 工具参数 Map（TOOL_CALL 类型时直接可用） */
    private Map<String, Object> arguments;

    // ===== ACTION 类型字段（旧版文本解析兼容） =====
    /** 工具参数（ACTION 类型时 - JSON 字符串） */
    private String actionParams;

    // ===== FINAL_ANSWER 类型字段 =====
    /** 最终答案 */
    private String finalAnswer;

    // ===== PARSE_ERROR 类型字段 =====
    /** 解析错误信息 */
    private String errorMessage;

    // ===== 通用字段 =====
    /** 原始响应文本 */
    private String rawResponse;

    // ===== 工厂方法 =====

    /**
     * 新版 TOOL_CALL 工厂方法（从 LLM tool calling 直接构造）
     */
    public static StructuredResponse toolCall(String toolName, Map<String, Object> arguments, String thought) {
        return StructuredResponse.builder()
                .type(ResponseType.TOOL_CALL)
                .toolName(toolName)
                .arguments(arguments)
                .thought(thought)
                .build();
    }

    /**
     * 从 ToolCall 构造（tool calling 返回后包装）
     */
    public static StructuredResponse fromToolCall(ToolCall toolCall) {
        return StructuredResponse.builder()
                .type(ResponseType.TOOL_CALL)
                .toolName(toolCall.getName())
                .arguments(toolCall.getArguments())
                .thought(toolCall.getThought())
                .build();
    }

    /**
     * 旧版 ACTION 工厂方法（保留兼容）
     * @deprecated 使用 toolCall() 替代
     */
    @Deprecated
    public static StructuredResponse action(String thought, String actionName, String actionParams, String raw) {
        Map<String, Object> args = parseArgs(actionParams);
        return StructuredResponse.builder()
                .type(ResponseType.ACTION)
                .thought(thought)
                .toolName(actionName)
                .actionParams(actionParams)
                .arguments(args)
                .rawResponse(raw)
                .build();
    }

    public static StructuredResponse finalAnswer(String thought, String answer, String raw) {
        return StructuredResponse.builder()
                .type(ResponseType.FINAL_ANSWER)
                .thought(thought)
                .finalAnswer(answer)
                .rawResponse(raw)
                .build();
    }

    public static StructuredResponse parseError(String errorMessage, String raw) {
        return StructuredResponse.builder()
                .type(ResponseType.PARSE_ERROR)
                .errorMessage(errorMessage)
                .rawResponse(raw)
                .build();
    }

    // ===== 兼容辅助方法 =====

    /**
     * 获取工具名称（统一入口）
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * 获取工具参数（统一入口，TOOL_CALL 返回 Map，ACTION 返回 JSON 字符串）
     */
    public Object getToolArguments() {
        return type == ResponseType.TOOL_CALL ? arguments : actionParams;
    }

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
