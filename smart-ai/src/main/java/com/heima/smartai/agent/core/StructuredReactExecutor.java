package com.heima.smartai.agent.core;

import com.heima.smartai.Config.AiClient;
import com.heima.smartai.agent.mcp.client.McpClientManager;
import com.heima.smartai.cache.AnswerCacheService;
import com.heima.smartai.cache.RetrievalCacheService;
import com.heima.smartai.model.AiAnalysisResult;
import com.heima.smartai.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化ReAct执行器（Tool Calling 版本）
 *
 * 架构升级：
 * - 移除 ParserChain / RegexResponseParser / JsonResponseParser
 * - 改用 LLM 原生 Function Calling，直接获取结构化 toolName + arguments
 * - 保留 ResponseValidator（验证工具调用和最终答案）
 * - 保留 AgentContext trace 机制
 * - 两层缓存：RetrievalCacheService（检索缓存）+ AnswerCacheService（答案缓存）
 * - 意图置信度低于0.6时自动转人工
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredReactExecutor {

    private final ToolFactory toolFactory;
    private final AiClient aiClient;
    private final ResponseValidator validator;
    private final McpClientManager mcpClientManager;
    private final RetrievalCacheService retrievalCacheService;
    private final AnswerCacheService answerCacheService;

    private static final int DEFAULT_MAX_STEPS = 5;
    /** 意图置信度转人工阈值 */
    private static final double INTENT_CONFIDENCE_THRESHOLD = 0.6;
    /** 答案缓存前缀 */
    private static final String ANSWER_CACHE_PREFIX = "ai:answer:cache:";

    /**
     * 执行结构化ReAct推理（Tool Calling 模式）
     * 两层缓存：RetrievalCacheService（检索缓存）+ AnswerCacheService（答案缓存）
     */
    public AiAnalysisResult execute(AgentContext context) {
        // 1. 检查答案缓存（第二层缓存）
        String cacheKey = ANSWER_CACHE_PREFIX + buildCacheKey(context);
        AiAnalysisResult cached = answerCacheService.get(cacheKey);
        if (cached != null) {
            log.info("答案缓存命中, ticketId={}", context.getTicketId());
            return cached;
        }

        int maxSteps = context.getMaxSteps() > 0 ? context.getMaxSteps() : DEFAULT_MAX_STEPS;
        List<Message> messages = new ArrayList<>();

        // 生成 Tool Schemas
        ToolSchemas toolSchemas = toolFactory.generateToolSchemas();

        // 系统提示（简化版，因为有 tool schemas 不需要详细格式说明）
        String systemPrompt = buildSystemPrompt();
        messages.add(Message.builder().role("system").content(systemPrompt).build());
        context.recordMessage("system", systemPrompt);

        // 用户问题
        String userInput = buildUserInput(context);
        messages.add(Message.ofUser(userInput));
        context.recordMessage("user", userInput);

        String lastObservation = null;

        for (int step = 1; step <= maxSteps; step++) {
            log.info("ReAct第{}步, ticketId={}", step, context.getTicketId());

            // 调用 LLM（带 Tool Calling）
            AiClient.LlmResult llmResult = callLLMWithTools(messages, toolSchemas);
            if (llmResult == null || (!llmResult.hasToolCalls() && llmResult.text() == null)) {
                log.warn("LLM响应为空");
                break;
            }

            // 处理工具调用
            if (llmResult.hasToolCalls()) {
                for (ToolCall toolCall : llmResult.toolCalls()) {
                    AiAnalysisResult result = handleToolCall(toolCall, messages, step, context);
                    if (result != null) {
                        // 写入答案缓存（第二层缓存）
                        answerCacheService.set(cacheKey, result);
                        return result; // 最终答案
                    }
                }
            } else {
                // LLM 返回文本（可能是最终答案或需要修正）
                String text = llmResult.text();
                log.debug("LLM文本回复: {}", text);
                context.recordMessage("assistant", text);

                // 检查是否是最终答案格式
                Optional<StructuredResponse> parsed = parseTextResponse(text);
                if (parsed.isPresent()) {
                    StructuredResponse response = parsed.get();

                    if (response.getType() == StructuredResponse.ResponseType.FINAL_ANSWER) {
                        AiAnalysisResult result = finishWithAnswer(response, context, step);
                        if (result != null) {
                            // 写入答案缓存（第二层缓存）
                            answerCacheService.set(cacheKey, result);
                            return result;
                        }
                    } else {
                        // 格式不对，发送修正提示
                        messages.add(Message.builder().role("assistant").content(text).build());
                        messages.add(Message.ofUser(response.getErrorMessage()));
                        context.recordMessage("user", response.getErrorMessage());
                    }
                } else {
                    // 无法识别格式，当作错误处理
                    messages.add(Message.builder().role("assistant").content(text).build());
                    String correction = "请使用工具调用或直接给出最终答案，不要输出其他格式。";
                    messages.add(Message.ofUser(correction));
                    context.recordMessage("user", correction);
                }
            }
        }

        // 达到最大步数，生成兜底答案
        if (lastObservation != null) {
            String fallback = generateFallbackAnswer(context.getQuestion(), lastObservation);
            context.setFinalAnswer(fallback);
            AiAnalysisResult result = parseFinalAnswer(fallback);
            answerCacheService.set(cacheKey, result);
            return result;
        }

        return new AiAnalysisResult("分析失败", "无法生成有效回答");
    }

    private String buildCacheKey(AgentContext context) {
        String key = context.getQuestion() + (context.getImageUrl() != null ? context.getImageUrl() : "");
        return org.springframework.util.DigestUtils.md5DigestAsHex(key.getBytes());
    }

    /**
     * 处理单个工具调用
     * @return 如果是最终答案返回 AiAnalysisResult，否则返回 null 继续循环
     */
    private AiAnalysisResult handleToolCall(ToolCall toolCall, List<Message> messages, int step, AgentContext context) {
        String toolName = toolCall.getName();
        Map<String, Object> arguments = toolCall.getArguments();

        log.info("Tool Call: {} args={}", toolName, arguments);

        // 验证工具调用
        ResponseValidator.ValidationResult validation = validator.validateToolCall(toolCall);
        if (!validation.isValid()) {
            log.warn("Tool Call 验证失败: {}", validation.getErrorMessage());
            messages.add(Message.ofUser(validation.getCorrectionPrompt()));
            return null;
        }

        context.addTrace("Step " + step + ": Tool:" + toolName + " args=" + arguments);

        // 添加 assistant 消息（包含 tool call）
        String toolCallDescription = "「调用工具 " + toolName + "，参数：" + arguments + "」";
        messages.add(Message.builder().role("assistant").content(toolCallDescription).build());
        context.recordMessage("assistant", toolCallDescription);

        // 执行工具
        SimpleTool.ToolResult toolResult = executeTool(toolName, arguments);
        String observation = toolResult.success() ? toolResult.output() : "Error: " + toolResult.error();

        context.addTrace("Observation: " + observation);
        String observationMsg = "工具执行结果：" + observation;
        messages.add(Message.ofUser(observationMsg));
        context.recordMessage("observation", observationMsg);

        // 检查意图分类置信度，低于阈值时转人工
        if ("intent_classify".equals(toolName)) {
            Double confidence = extractIntentConfidence(observation);
            if (confidence != null && confidence < INTENT_CONFIDENCE_THRESHOLD) {
                log.info("意图置信度 {} < {}，触发转人工", confidence, INTENT_CONFIDENCE_THRESHOLD);
                context.setFinished(true);
                context.addTrace("意图置信度低，转人工处理");
                return new AiAnalysisResult(
                        "意图识别置信度较低（" + confidence + "），无法准确判断用户需求",
                        "建议转人工客服处理，以便更好地帮助用户解决问题"
                );
            }
        }

        return null; // 继续循环
    }

    /**
     * 从意图分类结果中提取置信度
     */
    private Double extractIntentConfidence(String observation) {
        if (observation == null || observation.isBlank()) {
            return null;
        }
        Pattern pattern = Pattern.compile("置信度[:：]\\s*([0-9]+(?:\\.[0-9]+)?)");
        Matcher matcher = pattern.matcher(observation);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("解析置信度失败: {}", observation);
            }
        }
        return null;
    }

    /**
     * 处理最终答案
     * @return 如果验证通过返回 AiAnalysisResult，否则返回 null 让循环继续
     */
    private AiAnalysisResult finishWithAnswer(StructuredResponse response, AgentContext context, int step) {
        context.addTrace("Step " + step + ": Final Answer: " + response.getFinalAnswer());
        context.setFinished(true);
        context.setFinalAnswer(response.getFinalAnswer());

        // 验证最终答案
        ResponseValidator.ValidationResult validation = validator.validateFinalAnswer(response);
        if (!validation.isValid()) {
            log.warn("最终答案验证失败: {}", validation.getErrorMessage());
            // 发送修正提示，让 LLM 重新回答
            return null;
        }

        return parseFinalAnswer(response.getFinalAnswer());
    }

    /**
     * 解析文本响应（处理非 tool calling 模式的文本）
     */
    private Optional<StructuredResponse> parseTextResponse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        // 检查是否是最终答案格式
        if (text.contains("Final Answer") || text.contains("最终答案")) {
            String thought = "";
            String answer = text;

            int thoughtIdx = text.indexOf("Thought:");
            int answerIdx = text.indexOf("Final Answer:");
            if (thoughtIdx >= 0 && answerIdx > thoughtIdx) {
                thought = text.substring(thoughtIdx + 8, answerIdx).trim();
                answer = text.substring(answerIdx + 13).trim();
            }

            if (answer.length() > 5) {
                return Optional.of(StructuredResponse.finalAnswer(thought, answer, text));
            }
        }

        return Optional.of(StructuredResponse.parseError("无法识别的响应格式", text));
    }

    /**
     * 调用 LLM（带 Tool Calling）
     */
    private AiClient.LlmResult callLLMWithTools(List<Message> messages, ToolSchemas schemas) {
        try {
            return aiClient.chatWithTools(messages, schemas);
        } catch (Exception e) {
            log.error("LLM Tool Calling 失败", e);
            return new AiClient.LlmResult(null, null);
        }
    }

    private String buildSystemPrompt() {
        return """
            你是一个智能客服助手，擅长通过工具调用来解答用户问题。

            ## 工作流程
            1. 分析用户问题，决定是否需要调用工具
            2. 如果需要调用工具，使用 tool_call 格式
            3. 观察工具返回的结果
            4. 重复直到得到最终答案
            5. 最终答案使用 Final Answer 格式

            ## 最终答案格式
            必须包含"问题分析："和"解决建议："两部分

            ## 注意
            - 请根据工具的 description 选择合适的工具
            - 工具参数必须符合工具定义的参数类型
            """;
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

    /**
     * 执行工具
     */
    private SimpleTool.ToolResult executeTool(String toolName, Map<String, Object> arguments) {
        // 1. 先查本地工具
        SimpleTool localTool = toolFactory.getTool(toolName);
        if (localTool != null) {
            try {
                return localTool.execute(arguments);
            } catch (Exception e) {
                log.error("本地工具执行失败: tool={}, error={}", toolName, e.getMessage());
                return SimpleTool.ToolResult.fail("工具执行异常: " + e.getMessage());
            }
        }

        // 2. 查外部 MCP 工具 (格式: serverName::toolName)
        if (toolName.contains("::")) {
            String[] parts = toolName.split("::", 2);
            String serverName = parts[0];
            String mcpToolName = parts[1];
            try {
                return mcpClientManager.callExternalTool(serverName, mcpToolName, arguments);
            } catch (Exception e) {
                log.error("MCP工具执行失败: server={}, tool={}, error={}", serverName, mcpToolName, e.getMessage());
                return SimpleTool.ToolResult.fail("MCP工具执行异常: " + e.getMessage());
            }
        }

        return SimpleTool.ToolResult.fail("未知工具: " + toolName);
    }

    private String generateFallbackAnswer(String question, String lastObservation) {
        return "根据分析：" + lastObservation + "\n\n建议：如有疑问请咨询人工客服。";
    }

    private AiAnalysisResult parseFinalAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return new AiAnalysisResult("分析失败", "无法生成有效回答");
        }

        String[] parts = answer.split("解决建议[:：]");
        String analysis = parts.length > 0 ? parts[0].replace("问题分析：", "").replace("问题分析:", "").trim() : answer;
        String suggestion = parts.length > 1 ? parts[1].trim() : "请人工进一步核实。";

        return new AiAnalysisResult(analysis, suggestion);
    }
}
