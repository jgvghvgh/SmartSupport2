package com.heima.smartai.agent.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 结构化ReAct响应
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
        /** 调用工具 */
        TOOL_CALL,
        /** 最终答案 */
        FINAL_ANSWER,
        /** 解析失败 */
        PARSE_ERROR
    }

    private ResponseType type;

    /** 思考过程 */
    private String thought;

    /** 工具名称 */
    private String toolName;

    /** 工具参数 */
    private Map<String, Object> arguments;

    /** 最终答案 */
    private String finalAnswer;

    /** 解析错误信息 */
    private String errorMessage;

    /** 原始响应文本 */
    private String rawResponse;

    /**
     * 工具调用工厂方法
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
     * 从 ToolCall 构造
     */
    public static StructuredResponse fromToolCall(ToolCall toolCall) {
        return StructuredResponse.builder()
                .type(ResponseType.TOOL_CALL)
                .toolName(toolCall.getName())
                .arguments(toolCall.getArguments())
                .thought(toolCall.getThought())
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
}
