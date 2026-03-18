package com.heima.smartai.mapper;

import com.heima.smartai.model.TicketSummary;
import org.apache.ibatis.annotations.Insert;

public interface AiMapper {
    @Insert("insert into ticket_summary(ticket_id,ai_summary,satisfaction_score,created_at) values(#{ticketId},#{aiSummary},#{satisfactionScore},#{createdAt})")
    void insert(TicketSummary summary);
}
