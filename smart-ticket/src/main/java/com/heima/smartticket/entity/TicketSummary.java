package com.heima.smartticket.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketSummary {
    private Long id;
    private Long ticketId;
    private String aiSummary;
    private short satisfactionScore;
    private LocalDateTime createdAt;
}
