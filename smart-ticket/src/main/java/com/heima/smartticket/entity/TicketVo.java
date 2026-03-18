package com.heima.smartticket.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketVo {
    private List<TicketAttachment> attachments;
    private String content;
    private String createdAt;
    private String senderId;
}
