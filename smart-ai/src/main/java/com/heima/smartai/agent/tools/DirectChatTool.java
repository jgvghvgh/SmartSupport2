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
        return "当用户问题不属于知识库范畴，或需要AI基于通用知识回答时使用（兜底工具）。参数：question(必填), context(可选，上下文信息)";
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
