package com.heima.smartai.agent.service;

import com.heima.smartai.Config.AiClient;
import com.heima.smartai.agent.core.AgentContext;
import com.heima.smartai.agent.core.StructuredReactExecutor;
import com.heima.smartai.entity.TicketMessage;
import com.heima.smartai.mapper.TicketMessageMapper;
import com.heima.smartai.model.AiAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent服务 - 整合ReAct Loop、记忆和缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAiService {

    private final StructuredReactExecutor structuredExecutor;
    private final SimpleMemory memory;
    private final AiClient aiClient;
    private final TicketMessageMapper ticketMessageMapper;

    /**
     * 使用ReAct Agent处理问题
     * 两层缓存：StructuredReactExecutor 内部使用 RetrievalCacheService + AnswerCacheService
     */
    public AiAnalysisResult chat(String question, String ticketId, String imageUrl, String imageType) {
        // 构建AgentContext
        AgentContext context = AgentContext.builder()
                .ticketId(ticketId)
                .question(question)
                .imageUrl(imageUrl)
                .imageType(imageType)
                .maxSteps(10)
                .messageRecorder(msg -> {
                    // ReAct 过程中的消息不写入 ticket_message 表，只记录到 trace
                })
                .build();

        // 如果非新对话，加入历史上下文
        if (!memory.isNewConversation(Long.valueOf(ticketId))) {
            String history = memory.getFormattedHistory(Long.valueOf(ticketId));
            context.setQuestion(context.getQuestion() + "\n\n【对话历史】\n" + history);
        }

        // 执行ReAct推理（StructuredReactExecutor 内部处理两层缓存）
        AiAnalysisResult result = structuredExecutor.execute(context);

        // 保存AI最终答案到 ticket_message 表
        saveFinalAnswerToTicketMessage(ticketId, result);

        log.info("Agent处理完成, ticketId={}, traceSize={}", ticketId, context.getTrace().size());
        return result;
    }

    /**
     * 人工客服服务过程中，AI生成辅助回复建议
     * 读取工单的历史对话，生成适合人工客服发送的回复内容
     *
     * @param ticketId 工单ID
     * @param currentMessage 用户当前消息（需要回复的内容）
     * @return AI生成的辅助回复建议
     */
    public AiAnalysisResult generateAssistantReply(String ticketId, String currentMessage) {
        log.info("AI辅助回复生成, ticketId={}, currentMessage={}", ticketId, currentMessage);

        // 获取对话历史
        List<SimpleMemory.ChatMessage> history = memory.getHistory(Long.valueOf(ticketId));

        // 构建包含历史的prompt
        String historyText = buildHistoryText(history);
        String prompt = buildAssistantPrompt(historyText, currentMessage);

        // 调用LLM生成回复
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一个客服助手，擅长根据对话历史生成适合人工客服发送的回复建议。" +
                "请根据提供的对话历史和用户当前消息，生成一个专业、友好的回复建议。" +
                "回复应该包含：问题确认、理解表达、解决建议或安抚话语。");
        messages.add(systemMsg);

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        try {
            String response = aiClient.chatRaw(messages);
            if (response != null && !response.isBlank()) {
                return new AiAnalysisResult(
                        "根据对话历史生成的辅助回复",
                        response.trim()
                );
            }
        } catch (Exception e) {
            log.error("生成辅助回复失败, ticketId={}", ticketId, e);
        }

        return new AiAnalysisResult(
                "辅助回复生成失败",
                "抱歉，无法生成辅助回复，建议人工客服自行组织回复内容。"
        );
    }

    private String buildHistoryText(List<SimpleMemory.ChatMessage> history) {
        if (history.isEmpty()) {
            return "（暂无历史对话）";
        }
        StringBuilder sb = new StringBuilder();
        for (SimpleMemory.ChatMessage msg : history) {
            String role = "USER".equals(msg.senderType()) ? "用户" : "客服";
            sb.append(role).append("：").append(msg.content()).append("\n");
        }
        return sb.toString();
    }

    private String buildAssistantPrompt(String historyText, String currentMessage) {
        return """
                【对话历史】
                %s

                【用户当前消息】
                %s

                请根据以上对话历史，为人工客服生成一条合适的回复建议。
                """.formatted(historyText, currentMessage);
    }

    /**
     * 将AI最终答案保存到 ticket_message 表
     */
    private void saveFinalAnswerToTicketMessage(String ticketId, AiAnalysisResult result) {
        try {
            String content = "【问题分析】" + result.getProblemAnalysis()
                    + "\n\n【参考回复】" + result.getReferenceReply();
            TicketMessage message = TicketMessage.builder()
                    .ticketId(Long.valueOf(ticketId))
                    .senderType(TicketMessage.SENDER_AI)
                    .content(content)
                    .isAi(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            ticketMessageMapper.insert(message);
        } catch (Exception e) {
            log.error("保存AI最终答案到ticket_message表失败, ticketId={}", ticketId, e);
        }
    }
}
