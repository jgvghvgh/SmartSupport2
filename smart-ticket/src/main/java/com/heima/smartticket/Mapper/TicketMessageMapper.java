package com.heima.smartticket.Mapper;

import com.heima.smartticket.entity.TicketMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TicketMessageMapper {
    @Insert("insert into ticket_message(ticket_id,content,sender_id,sender_type) values(#{ticketId},#{content},#{senderId},#{senderType})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    Long insert(TicketMessage message);

    @Select("select count(*) from ticket_message where ticket_id = #{ticketId}")
    Long countByTicketId(Long ticketId);

    @Select("select exists(select 1 from ticket_message where ticket_id = #{ticketId} and sender_type = 'AI')")
    Boolean existsAiMessage(Long ticketId);

    @Select("select exists(select 1 from ticket_message where id = #{messageid})")
    Boolean exists(Long messageid);
    @Select("select * from ticket_message where ticket_id = #{ticketId}")
    List<TicketMessage> getComment(Long ticketId);
}
