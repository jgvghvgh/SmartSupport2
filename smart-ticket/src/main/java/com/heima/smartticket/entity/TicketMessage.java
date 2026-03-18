package com.heima.smartticket.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketMessage {
    private Long id;
    private Long ticketId;
    private Long senderId;
    private String senderType;
    private String content;
    private short isAi;
    private LocalDateTime createdAt;
}
