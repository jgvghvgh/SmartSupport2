package com.heima.smartai.agent.service;

import com.heima.smartai.AiService.AiService;
import com.heima.smartai.model.AiAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI服务代理 - 支持Original模式和Agent模式切换
 * 通过配置 ai.mode = original | agent 来切换
 */
@Slf4j
@Component
public class AiServiceProxy implements AiService {

    @Value("${ai.mode:original}")
    private String mode;

    private final AiService originalAiService;
    private final AgentAiService agentAiService;

    public AiServiceProxy(
            @Qualifier("aiServiceImpl") AiService originalAiService,
            AgentAiService agentAiService) {
        this.originalAiService = originalAiService;
        this.agentAiService = agentAiService;
    }

    @Override
    public AiAnalysisResult chat(String message, String ticketId) {
        return chat(message, ticketId, null, null);
    }

    @Override
    public AiAnalysisResult chat(String message, String ticketId, String imageUrl, String imageType) {
        if ("agent".equalsIgnoreCase(mode)) {
            log.info("使用Agent模式处理, ticketId={}", ticketId);
            return agentAiService.chat(message, ticketId, imageUrl, imageType);
        } else {
            log.info("使用Original模式处理, ticketId={}", ticketId);
            return originalAiService.chat(message, ticketId, imageUrl, imageType);
        }
    }
}
