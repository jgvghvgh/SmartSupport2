package com.heima.smartticket.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserTicketRankVO {
    private Long userId;
    private Integer count;
}