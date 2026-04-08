package com.heima.smartai.agent.tools;

import com.heima.smartai.agent.core.SimpleTool;
import com.heima.smartai.intent.IntentClassifierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 意图分类工具
 * 判断用户问题的意图类型
 */
@Component
@RequiredArgsConstructor
public class IntentClassifyTool implements SimpleTool {

    private final IntentClassifierService intentClassifierService;

    @Override
    public String name() {
        return "intent_classify";
    }

    @Override
    public String description() {
        return """
            判断用户问题的意图类型，帮助选择合适的后续处理方式。

            【使用场景】
            - 不确定用户想要什么，需要先分类再处理
            - 需要根据意图选择不同的处理流程
            - 作为入口判断，决定走知识库、FAQ还是人工

            【返回意图类型】
            - FAQ：用户问题属于常见问题，可以从知识库或FAQ中找到标准答案
            - NEED_MORE_INFO：用户提供的信息不足，无法直接回答，需要追问
            - OTHER：不属于上述类型，可能需要转人工或通用对话处理

            【返回内容】
            - 意图类型（FAQ / NEED_MORE_INFO / OTHER）
            - 置信度（0-1之间的数值，越高越准确）
            - 判断原因（为什么分类到这个意图）

            【使用建议】
            - 通常作为第一个工具调用，在明确用户意图后选择后续处理
            - 置信度低于0.6时建议谨慎处理，可能需要转人工
            """;
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of(
                                "type", "string",
                                "description", "用户的问题或发言内容"
                        ),
                        "user_id", Map.of(
                                "type", "string",
                                "description", "用户ID（可选），用于个性化意图判断"
                        )
                ),
                "required", java.util.List.of("question")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String question = (String) params.get("question");
        String userId = params.containsKey("user_id") ? (String) params.get("user_id") : "default";

        if (question == null || question.isBlank()) {
            return ToolResult.fail("question参数不能为空");
        }

        IntentClassifierService.IntentResult result = intentClassifierService.classify(question, userId);
        if (!result.ok) {
            return ToolResult.fail("意图分类失败: " + result.reason);
        }

        String output = String.format("意图: %s, 置信度: %.2f, 原因: %s",
                result.intent, result.confidence, result.reason);
        return ToolResult.ok(output);
    }
}
