package com.heima.smartai.mapper;

import com.heima.smartai.entity.TicketMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 工单消息Mapper（对应 ticket_message 表）
 */
@Mapper
public interface TicketMessageMapper {

    @Insert("INSERT INTO ticket_message(ticket_id, sender_id, sender_type, content, is_ai, created_at) " +
            "VALUES(#{ticketId}, #{senderId}, #{senderType}, #{content}, #{isAi}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(TicketMessage message);

    @Select("SELECT * FROM ticket_message WHERE ticket_id = #{ticketId} ORDER BY created_at ASC")
    List<TicketMessage> findByTicketId(@Param("ticketId") Long ticketId);

    @Select("SELECT * FROM ticket_message WHERE ticket_id = #{ticketId} ORDER BY created_at DESC LIMIT #{limit}")
    List<TicketMessage> findRecentByTicketId(@Param("ticketId") Long ticketId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM ticket_message WHERE ticket_id = #{ticketId}")
    Long countByTicketId(@Param("ticketId") Long ticketId);

    @Delete("DELETE FROM ticket_message WHERE ticket_id = #{ticketId}")
    void deleteByTicketId(@Param("ticketId") Long ticketId);
}
