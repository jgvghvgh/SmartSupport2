package com.heima.smartai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工单消息记录（对应 ticket_message 表）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketMessage {
    private Long id;
    private Long ticketId;
    private Long senderId;
    /** 发送者类型：USER / AGENT / SYSTEM / AI */
    private String senderType;
    private String content;
    /** 是否为AI生成的消息 */
    private Boolean isAi;
    private LocalDateTime createdAt;

    /**
     * senderType 枚举值
     */
    public static final String SENDER_USER = "USER";
    public static final String SENDER_AGENT = "AGENT";
    public static final String SENDER_SYSTEM = "SYSTEM";
    public static final String SENDER_AI = "AI";

    /**
     * 从 ReAct role 转换为 senderType
     */
    public static String toSenderType(String role) {
        return switch (role) {
            case "user" -> SENDER_USER;
            case "assistant" -> SENDER_AI;
            case "system" -> SENDER_SYSTEM;
            case "observation" -> SENDER_SYSTEM;
            default -> SENDER_SYSTEM;
        };
    }

    /**
     * 从 role 判断是否 AI 消息
     */
    public static boolean isAiMessage(String role) {
        return "assistant".equals(role) || "observation".equals(role);
    }
}
