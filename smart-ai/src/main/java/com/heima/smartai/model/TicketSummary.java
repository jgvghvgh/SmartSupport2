package com.heima.smartai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketSummary {
    private Long id;
    private Long TicketId;
    private String aiSummary;
    private short satisfactionScore;
    private LocalDateTime createdAt;
}
