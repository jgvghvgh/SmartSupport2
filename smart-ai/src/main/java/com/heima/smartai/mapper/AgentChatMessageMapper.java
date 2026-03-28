package com.heima.smartai.mapper;

import com.heima.smartai.entity.AgentChatMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Agent对话历史Mapper
 */
@Mapper
public interface AgentChatMessageMapper {

    @Insert("insert into agent_chat_message(ticket_id, role, content, created_at) " +
            "values(#{ticketId}, #{role}, #{content}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AgentChatMessage message);

    @Select("select * from agent_chat_message where ticket_id = #{ticketId} order by created_at asc")
    List<AgentChatMessage> findByTicketId(@Param("ticketId") String ticketId);

    @Select("select * from agent_chat_message where ticket_id = #{ticketId} order by created_at desc limit #{limit}")
    List<AgentChatMessage> findRecentByTicketId(@Param("ticketId") String ticketId, @Param("limit") int limit);

    @Select("select count(*) from agent_chat_message where ticket_id = #{ticketId}")
    Long countByTicketId(@Param("ticketId") String ticketId);

    @Delete("delete from agent_chat_message where ticket_id = #{ticketId}")
    void deleteByTicketId(@Param("ticketId") String ticketId);
}
