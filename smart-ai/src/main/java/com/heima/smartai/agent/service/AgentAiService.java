package com.heima.smartai.agent.service;

import com.heima.smartai.agent.core.AgentContext;
import com.heima.smartai.agent.core.ReActLoop;
import com.heima.smartai.cache.AnswerCacheService;
import com.heima.smartai.client.TicketRemoteClient;
import com.heima.smartai.model.AiAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent服务 - 整合ReAct Loop、记忆和缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAiService {

    private final ReActLoop reActLoop;
    private final SimpleMemory memory;
    private final AnswerCacheService answerCacheService;
    private final TicketRemoteClient ticketRemoteClient;

    /**
     * 使用ReAct Agent处理问题
     */
    public AiAnalysisResult chat(String question, String ticketId, String imageUrl, String imageType) {
        // 1. 检查答案缓存
        String cacheKey = buildCacheKey(question, imageUrl);
        AiAnalysisResult cached = answerCacheService.get(cacheKey);
        if (cached != null) {
            log.info("Agent答案缓存命中, ticketId={}", ticketId);
            return cached;
        }

        // 2. 构建AgentContext
        AgentContext context = AgentContext.builder()
                .ticketId(ticketId)
                .question(question)
                .imageUrl(imageUrl)
                .imageType(imageType)
                .maxSteps(10)
                .build();

        // 3. 如果非新对话，加入历史上下文
        if (!memory.isNewConversation(ticketId)) {
            String history = memory.getFormattedHistory(ticketId);
            context.setQuestion(context.getQuestion() + "\n\n【对话历史】\n" + history);
        }

        // 4. 执行ReAct推理
        AiAnalysisResult result = reActLoop.execute(context);

        // 5. 保存到记忆
        memory.addUserMessage(ticketId, question);
        memory.addAssistantMessage(ticketId, result.getProblemAnalysis() + "\n" + result.getReferenceReply());

        // 6. 异步保存AI回复到工单
        saveAiMessageAsync(ticketId, result);

        // 7. 写入答案缓存
        answerCacheService.set(cacheKey, result);

        log.info("Agent处理完成, ticketId={}, traceSize={}", ticketId, context.getTrace().size());
        return result;
    }

    private String buildCacheKey(String question, String imageUrl) {
        String key = question + (imageUrl != null ? imageUrl : "");
        return org.springframework.util.DigestUtils.md5DigestAsHex(key.getBytes());
    }

    private void saveAiMessageAsync(String ticketId, AiAnalysisResult result) {
        try {
            String content = "【问题分析】" + result.getProblemAnalysis()
                    + "\n\n【参考回复】" + result.getReferenceReply();
            ticketRemoteClient.saveAiMessage(Long.valueOf(ticketId), content);
        } catch (Exception e) {
            log.error("保存AI消息失败, ticketId={}", ticketId, e);
        }
    }
}
