package com.heima.smartticket.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Ticket {
    private Long id;
    private String title;
    private String description;
    private String status;
    private String priority;
    private Long userId;
    private Long assigneeId;
    private String origin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
