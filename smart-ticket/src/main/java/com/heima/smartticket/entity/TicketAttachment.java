package com.heima.smartticket.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketAttachment {
    private Long id;
    private Long ticketId;
    private String fileName;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
    private String createdAt;
    private String uploaderId;
}
