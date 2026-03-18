package com.heima.smartticket.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketOverviewVO {
    private Integer totalTickets;
    private Integer newTickets;
    private Integer inProgress;
    private Integer resolved;
    private Integer closed;
}