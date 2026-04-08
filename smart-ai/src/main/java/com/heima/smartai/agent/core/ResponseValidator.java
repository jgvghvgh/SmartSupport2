package com.heima.smartai.agent.core;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化响应验证器
 * 验证解析结果是否符合预期，不符合时返回修正提示
 */
@Slf4j
@Component
public class ResponseValidator {

    /**
     * 验证结果
     */
    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private String errorMessage;
        private String correctionPrompt;

        public static ValidationResult ok() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult error(String error, String correction) {
            return new ValidationResult(false, error, correction);
        }
    }

    /**
     * 验证 ToolCall
     */
    public ValidationResult validateToolCall(ToolCall toolCall) {
        List<String> errors = new ArrayList<>();

        if (toolCall.getName() == null || toolCall.getName().isBlank()) {
            errors.add("缺少工具名称");
        }

        if (toolCall.getArguments() == null) {
            errors.add("缺少工具参数");
        }

        // 验证工具名称是否为有效标识符
        if (toolCall.getName() != null && !isValidIdentifier(toolCall.getName())) {
            errors.add("工具名称不合法: " + toolCall.getName());
        }

        if (!errors.isEmpty()) {
            String error = String.join(", ", errors);
            String correction = buildToolCallCorrectionPrompt(toolCall, error);
            return ValidationResult.error(error, correction);
        }

        return ValidationResult.ok();
    }

    /**
     * 验证FinalAnswer类型的响应
     */
    public ValidationResult validateFinalAnswer(StructuredResponse response) {
        List<String> errors = new ArrayList<>();

        if (response.getFinalAnswer() == null || response.getFinalAnswer().isBlank()) {
            errors.add("缺少最终答案(finalAnswer)");
        }

        if (response.getFinalAnswer() != null && response.getFinalAnswer().length() < 5) {
            errors.add("最终答案过短，可能未正确解析");
        }

        if (!errors.isEmpty()) {
            String error = String.join(", ", errors);
            String correction = buildFinalAnswerCorrectionPrompt(response, error);
            return ValidationResult.error(error, correction);
        }

        return ValidationResult.ok();
    }

    private boolean isValidIdentifier(String name) {
        // 允许字母、数字、下划线，以及MCP工具的server::tool格式
        return name.matches("^\\w+$") || name.matches("^\\w+::\\w+$");
    }

    private String buildToolCallCorrectionPrompt(ToolCall toolCall, String error) {
        return String.format("""
            工具调用验证失败: %s

            请确保：
            1. 工具名称必须是已定义工具之一
            2. 参数必须是符合工具 schema 定义的有效 JSON 对象

            上次调用：tool=%s, arguments=%s
            """, error, toolCall.getName(), toolCall.getArguments());
    }

    private String buildFinalAnswerCorrectionPrompt(StructuredResponse response, String error) {
        return String.format("""
            最终答案格式不正确: %s

            请确保Final Answer包含：
            1. 问题分析：<对问题的分析>
            2. 解决建议：<具体的解决方案>

            示例：
            Final Answer: 问题分析：用户反映无法登录系统，可能是密码错误或账号被锁定。
            解决建议：1. 请尝试找回密码 2. 如仍无法解决请联系客服
            """, error);
    }
}
