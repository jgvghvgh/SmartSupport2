package com.heima.smartai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent对话历史记录（数据库持久化）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentChatMessage {
    private Long id;
    private String ticketId;
    private String role;           // user / assistant
    private String content;
    private LocalDateTime createdAt;
}
