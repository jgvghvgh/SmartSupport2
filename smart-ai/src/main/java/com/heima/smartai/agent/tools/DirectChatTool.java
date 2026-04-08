package com.heima.smartai.agent.tools;

import com.heima.smartai.agent.core.SimpleTool;
import com.heima.smartai.Config.AiClient;
import com.heima.smartai.model.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 直接对话工具（兜底）
 * 当其他工具都不适用时，使用 AI 通用能力回答
 */
@Component
@RequiredArgsConstructor
public class DirectChatTool implements SimpleTool {

    private final AiClient aiClient;

    @Override
    public String name() {
        return "direct_chat";
    }

    @Override
    public String description() {
        return """
            直接与 AI 对话，使用通用知识回答用户问题（兜底工具）。

            【使用场景】
            - 用户问题不属于知识库范畴（如闲聊、问候、通用咨询）
            - 所有检索工具都无法找到相关信息
            - 需要 AI 基于通用知识（非知识库）来回答
            - 用户明确要求 AI 直接回答而不需要检索

            【与 kb_search 的区别】
            - kb_search：从知识库检索内容后 AI 整合回答
            - direct_chat：直接调用 AI 通用能力，不检索知识库

            【返回内容】
            - AI 直接生成的回答
            - 通常包含问题分析和解决建议

            【使用建议】
            - 这是兜底工具，其他工具优先
            - 当不确定用什么工具时，可以先用 intent_classify 判断
            - 如果知识库检索结果不满意，最后可以用这个工具获得 AI 通用回答
            """;
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of(
                                "type", "string",
                                "description", "用户的完整问题"
                        ),
                        "context", Map.of(
                                "type", "string",
                                "description", "上下文信息（可选），如之前的对话内容、工单信息等，帮助AI更好地理解问题"
                        )
                ),
                "required", java.util.List.of("question")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String question = (String) params.get("question");
        String context = params.containsKey("context") ? (String) params.get("context") : null;

        if (question == null || question.isBlank()) {
            return ToolResult.fail("question参数不能为空");
        }

        String prompt = buildPrompt(question, context);
        String aiText = aiClient.chat(List.of(Message.ofUser(prompt)));

        if (aiText == null || aiText.isBlank()) {
            return ToolResult.fail("AI响应为空");
        }
        return ToolResult.ok(aiText);
    }

    private String buildPrompt(String question, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个客服专家，请直接回答用户问题。\n\n");
        prompt.append("用户问题：\n").append(question).append("\n\n");
        if (context != null && !context.isBlank()) {
            prompt.append("上下文信息：\n").append(context).append("\n\n");
        }
        prompt.append("请回答：\n1. 问题分析\n2. 解决建议");
        return prompt.toString();
    }
}
