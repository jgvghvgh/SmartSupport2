package com.heima.smartticket.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OutboxMessage {
    private int id;
    private String eventType;
    private String payload;
    private String status;
    private LocalDateTime createTime;
    private int retryCount;
}
