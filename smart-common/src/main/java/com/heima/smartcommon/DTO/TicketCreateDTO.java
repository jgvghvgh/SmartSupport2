package com.heima.smartcommon.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketCreateDTO {
    private String title;
    private String description;
    private Long userId;
}
