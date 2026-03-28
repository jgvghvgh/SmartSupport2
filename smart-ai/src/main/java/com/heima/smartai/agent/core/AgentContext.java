package com.heima.smartai.agent.core;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent执行上下文
 */
@Data
@Builder
public class AgentContext {
    private String ticketId;
    private String question;
    private String imageUrl;
    private String imageType;
    private int maxSteps;
    private boolean finished;
    private String finalAnswer;
    @Builder.Default
    private List<String> trace = new ArrayList<>();

    public void addTrace(String step) {
        trace.add(step);
    }
}
