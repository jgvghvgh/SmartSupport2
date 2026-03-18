package com.heima.smartcommon.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketMessageDTO {
    private Long ticketId;
    private String content;
    private Long senderId;
}
