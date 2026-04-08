package com.heima.smartai.agent.core;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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

    /**
     * 消息记录器：ReAct 过程中每条消息都实时回调保存
     * 类型：role ("user"/"assistant"/"observation")，content
     */
    @Builder.Default
    private Consumer<MessageRecord> messageRecorder = msg -> {};

    public void addTrace(String step) {
        trace.add(step);
    }

    /**
     * 记录一条消息
     */
    public void recordMessage(String role, String content) {
        messageRecorder.accept(new MessageRecord(role, content));
    }

    /**
     * 消息记录
     */
    public record MessageRecord(String role, String content) {}
}
