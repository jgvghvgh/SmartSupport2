package com.heima.smartai.agent.core;

import com.heima.smartai.Config.AiClient;
import com.heima.smartai.agent.core.SimpleTool;
import com.heima.smartai.agent.mcp.client.McpClientManager;
import com.heima.smartai.model.AiAnalysisResult;
import com.heima.smartai.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct推理循环
 * 核心：Thought -> Action -> Observation -> ... -> Final Answer
 * 支持本地工具和外部MCP工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReActLoop {

    private final ToolFactory toolFactory;
    private final AiClient aiClient;
    private final McpClientManager mcpClientManager;

    private static final int DEFAULT_MAX_STEPS = 10;

    // 解析正则
    private static final Pattern THOUGHT_ACTION_PATTERN =
            Pattern.compile("Thought:\\s*(.+?)\\s*Action:\\s*(\\w+)\\[([^\\]]*)\\]", Pattern.DOTALL);
    private static final Pattern THOUGHT_FINAL_PATTERN =
            Pattern.compile("Thought:\\s*(.+?)\\s*Final Answer:\\s*(.+)", Pattern.DOTALL);

    /**
     * 执行ReAct推理
     */
    public AiAnalysisResult execute(AgentContext context) {
        int maxSteps = context.getMaxSteps() > 0 ? context.getMaxSteps() : DEFAULT_MAX_STEPS;
        List<Message> messages = new ArrayList<>();

        // 系统提示
        String systemPrompt = ReActSystemPrompt.build(toolFactory.generateToolDefinitions());
        messages.add(Message.builder().role("system").content(systemPrompt).build());

        // 用户问题
        String userInput = buildUserInput(context);
        messages.add(Message.ofUser(userInput));

        String lastObservation = null;

        for (int step = 1; step <= maxSteps; step++) {
            log.info("ReAct第{}步, ticketId={}", step, context.getTicketId());

            // 调用LLM
            String llmResponse = callLLM(messages);
            if (llmResponse == null || llmResponse.isBlank()) {
                log.warn("LLM响应为空");
                break;
            }

            log.debug("LLM响应: {}", llmResponse);

            // 检查Final Answer
            Matcher finalMatcher = THOUGHT_FINAL_PATTERN.matcher(llmResponse);
            if (finalMatcher.find()) {
                String thought = finalMatcher.group(1).trim();
                String answer = finalMatcher.group(2).trim();
                context.addTrace("Step " + step + ": " + thought + "\nFinal Answer: " + answer);
                context.setFinished(true);
                context.setFinalAnswer(answer);
                return parseFinalAnswer(answer);
            }

            // 解析Thought+Action
            Matcher actionMatcher = THOUGHT_ACTION_PATTERN.matcher(llmResponse);
            if (!actionMatcher.find()) {
                log.warn("无法解析Thought/Action，使用兜底");
                break;
            }

            String thought = actionMatcher.group(1).trim();
            String toolName = actionMatcher.group(2);
            String toolInput = actionMatcher.group(3).trim();

            context.addTrace("Step " + step + ": " + thought + "\nAction: " + toolName + "[" + toolInput + "]");

            // 添加assistant消息
            messages.add(Message.builder().role("assistant").content(llmResponse).build());

            // 执行工具
            SimpleTool.ToolResult toolResult = executeTool(toolName, toolInput);
            lastObservation = toolResult.success() ? toolResult.output() : "Error: " + toolResult.error();

            context.addTrace("Observation: " + lastObservation);

            // 添加观察结果
            messages.add(Message.ofUser("Observation: " + lastObservation));
        }

        // 达到最大步数或解析失败，生成兜底答案
        if (lastObservation != null) {
            String fallback = generateFallbackAnswer(context.getQuestion(), lastObservation);
            context.setFinalAnswer(fallback);
            return parseFinalAnswer(fallback);
        }

        return new AiAnalysisResult("分析失败", "无法生成有效回答");
    }

    private String buildUserInput(AgentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题：").append(context.getQuestion()).append("\n");
        if (context.getTicketId() != null) {
            sb.append("工单ID：").append(context.getTicketId()).append("\n");
        }
        if (context.getImageUrl() != null) {
            sb.append("图片URL：").append(context.getImageUrl()).append("\n");
            sb.append("图片类型：").append(context.getImageType() != null ? context.getImageType() : "unknown").append("\n");
        }
        return sb.toString();
    }

    private String callLLM(List<Message> messages) {
        try {
            return aiClient.chat(messages);
        } catch (Exception e) {
            log.error("LLM调用失败", e);
            return null;
        }
    }

    private SimpleTool.ToolResult executeTool(String toolName, String toolInput) {
        // 1. 先查本地工具
        SimpleTool localTool = toolFactory.getTool(toolName);
        if (localTool != null) {
            try {
                Map<String, Object> params = parseJson(toolInput);
                return localTool.execute(params);
            } catch (Exception e) {
                log.error("本地工具执行失败: tool={}, error={}", toolName, e.getMessage());
                return SimpleTool.ToolResult.fail("工具执行异常: " + e.getMessage());
            }
        }

        // 2. 查外部MCP工具 (格式: serverName::toolName)
        if (toolName.contains("::")) {
            String[] parts = toolName.split("::", 2);
            String serverName = parts[0];
            String mcpToolName = parts[1];
            try {
                Map<String, Object> params = parseJson(toolInput);
                return mcpClientManager.callExternalTool(serverName, mcpToolName, params);
            } catch (Exception e) {
                log.error("MCP工具执行失败: server={}, tool={}, error={}", serverName, mcpToolName, e.getMessage());
                return SimpleTool.ToolResult.fail("MCP工具执行异常: " + e.getMessage());
            }
        }

        return SimpleTool.ToolResult.fail("未知工具: " + toolName);
    }

    private Map<String, Object> parseJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) {
            return Map.of();
        }
        try {
            return com.alibaba.fastjson2.JSON.parseObject(jsonStr);
        } catch (Exception e) {
            log.warn("JSON解析失败，尝试简化解析: {}", jsonStr);
            return Map.of("query", jsonStr);
        }
    }

    private String generateFallbackAnswer(String question, String lastObservation) {
        return "根据分析：" + lastObservation + "\n\n建议：如有疑问请咨询人工客服。";
    }

    private AiAnalysisResult parseFinalAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return new AiAnalysisResult("分析失败", "无法生成有效回答");
        }

        String[] parts = answer.split("解决建议[:：]");
        String analysis = parts.length > 0 ? parts[0].replace("问题分析：", "").trim() : answer;
        String suggestion = parts.length > 1 ? parts[1].trim() : "请人工进一步核实。";

        return new AiAnalysisResult(analysis, suggestion);
    }
}
