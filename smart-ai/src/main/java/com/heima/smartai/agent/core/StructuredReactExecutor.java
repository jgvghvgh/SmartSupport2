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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 结构化ReAct执行器（Tool Calling 版本）
 *
 * 架构升级：
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

    /** LLM 输出类型枚举 */
    private enum LlmOutputType {
        /** 最终答案（包含问题分析和解决建议） */
        FINAL_ANSWER,
        /** 思考过程/推理过程 */
        THINKING,
        /** 拒绝回答 */
        REFUSE,
        /** 空响应 */
        EMPTY,
        /** 未知/其他 */
        UNKNOWN
    }

    /** 最终答案匹配模式 */
    private static final Pattern FINAL_ANSWER_PATTERN;
    /** 思考过程匹配模式 */
    private static final Pattern THINKING_PATTERN;
    /** 拒绝回答匹配模式 */
    private static final Pattern REFUSE_PATTERN;

    static {
        try {
            FINAL_ANSWER_PATTERN = Pattern.compile("问题分析[：:]|解决建议[：:]|Final\\s*Answer", Pattern.CASE_INSENSITIVE);
            THINKING_PATTERN = Pattern.compile("^ думаю |^ thinking |^ 分析中 |^ 思考中 |内部推理|思维过程|让我想想", Pattern.CASE_INSENSITIVE);
            REFUSE_PATTERN = Pattern.compile("无法回答|不能回答|抱歉|对不起|拒绝|无法提供", Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            throw new RuntimeException("正则表达式初始化失败", e);
        }
    }

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

        // 系统提示
        String systemPrompt = buildSystemPrompt();
        messages.add(Message.builder().role("system").content(systemPrompt).build());
        context.recordMessage("system", systemPrompt);

        // 用户问题
        String userInput = buildUserInput(context);
        messages.add(Message.ofUser(userInput));
        context.recordMessage("user", userInput);

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
                // LLM 返回非 tool calling 文本，需要分类处理
                String text = llmResult.text();
                LlmOutputType outputType = classifyLlmOutput(text);

                switch (outputType) {
                    case FINAL_ANSWER:
                        // 直接返回最终答案
                        log.info("LLM直接返回最终答案");
                        AiAnalysisResult result = parseFinalAnswer(text);
                        answerCacheService.set(cacheKey, result);
                        return result;

                    case THINKING:
                        // 思考过程添加到记忆，继续循环
                        log.info("LLM思考过程: {}", text);
                        messages.add(Message.builder().role("assistant").content(text).build());
                        context.recordMessage("assistant", "[思考] " + text);
                        // 提示LLM继续
                        messages.add(Message.ofUser("请基于以上分析继续回答，或使用工具调用。"));
                        context.recordMessage("user", "请基于以上分析继续回答，或使用工具调用。");
                        break;

                    case REFUSE:
                        // LLM拒绝回答
                        log.warn("LLM拒绝回答: {}", text);
                        messages.add(Message.builder().role("assistant").content(text).build());
                        context.recordMessage("assistant", "[拒绝] " + text);
                        String refusePrompt = "请尝试调用合适的工具来回答用户问题。";
                        messages.add(Message.ofUser(refusePrompt));
                        context.recordMessage("user", refusePrompt);
                        break;

                    case EMPTY:
                        log.warn("LLM响应为空文本");
                        break;

                    case UNKNOWN:
                    default:
                        // 未知类型，当作需要工具调用处理
                        log.warn("LLM返回未知格式文本: {}", text);
                        messages.add(Message.builder().role("assistant").content(text).build());
                        context.recordMessage("assistant", text);
                        String correction = "请使用工具调用来回答问题。";
                        messages.add(Message.ofUser(correction));
                        context.recordMessage("user", correction);
                        break;
                }
            }
        }

        // 达到最大步数，生成兜底答案
        if (context.getLastObservation() != null) {
            String fallback = generateFallbackAnswer(context.getQuestion(), context.getLastObservation());
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
            // 先把 tool call 添加到记忆，让 LLM 知道它之前的调用被拒绝了
            String toolCallDescription = "「调用工具 " + toolName + "，参数：" + arguments + "」";
            messages.add(Message.builder().role("assistant").content(toolCallDescription).build());
            context.recordMessage("assistant", "[验证失败] " + toolCallDescription);
            // 添加修正提示
            messages.add(Message.ofUser(validation.getCorrectionPrompt()));
            context.recordMessage("user", "[验证失败修正] " + validation.getCorrectionPrompt());
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

        // 更新 lastObservation 到 context
        context.setLastObservation(observation);

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

        // 工具执行成功后，添加"请基于观察结果继续"的提示
        String continuePrompt = "请基于以上工具执行结果，继续分析并回答用户问题。如果已有足够信息给出最终答案，请按格式输出；如果需要更多工具，请继续调用。";
        messages.add(Message.ofUser(continuePrompt));
        context.recordMessage("user", "[继续提示] " + continuePrompt);

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

            ## ReAct 工作流程
            1. 【思考】分析用户问题，判断需要哪些信息
            2. 【行动】如果需要调用工具，使用 tool_call 格式
            3. 【观察】查看工具返回的结果
            4. 重复步骤 1-3，直到得到足够信息
            5. 【回答】给出最终答案

            ## 输出类型（你必须且只能选择其中一种）
            - **tool_call**：需要获取外部信息时调用工具
            - **final_answer**：已有足够信息，直接给出最终答案
            - **thinking**：当前正在分析问题或等待工具结果，可以是一些思考过程的描述
            - **refuse**：如果问题无法回答或不适合回答，给出拒绝回答的理由
            - **unknown**：当你不确定如何回答或工具调用时，可以输出一些提示信息，但不符合上述类型

            ## 最终答案格式（必须同时包含两部分）
            问题分析：[对用户问题的分析]
            解决建议：[具体的解决步骤或建议]

            ## 注意事项
            - 请根据工具的 description 选择合适的工具
            - 工具参数必须符合工具定义的参数类型
            - 如果工具返回的信息已经足够回答用户问题，立即给出最终答案，不要继续调用工具
            - 避免重复调用同一个工具获取相同信息
            - 如果工具执行失败，说明原因并尝试其他方式
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

    /**
     * 对 LLM 输出进行分类
     */
    private LlmOutputType classifyLlmOutput(String text) {
        if (text == null || text.isBlank()) {
            return LlmOutputType.EMPTY;
        }

        String trimmedText = text.trim();

        // 检查是否包含最终答案的特征
        if (FINAL_ANSWER_PATTERN.matcher(trimmedText).find()) {
            return LlmOutputType.FINAL_ANSWER;
        }

        // 检查是否是思考过程
        if (THINKING_PATTERN.matcher(trimmedText).find()) {
            return LlmOutputType.THINKING;
        }

        // 检查是否是拒绝回答
        if (REFUSE_PATTERN.matcher(trimmedText).find()) {
            return LlmOutputType.REFUSE;
        }

        // 短文本（工具名称等）视为UNKNOWN
        if (trimmedText.length() < 10) {
            return LlmOutputType.UNKNOWN;
        }

        return LlmOutputType.UNKNOWN;
    }
}
