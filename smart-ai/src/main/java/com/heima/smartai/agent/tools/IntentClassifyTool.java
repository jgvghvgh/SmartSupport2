package com.heima.smartai.agent.tools;

import com.heima.smartai.agent.core.SimpleTool;
import com.heima.smartai.intent.IntentClassifierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 意图分类工具
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
        return "当需要判断用户问题的意图类型时使用。返回三种意图之一：FAQ（常见问题）、NEED_MORE_INFO（信息不足）、OTHER（其他）。参数：question(必填), user_id(可选)";
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
