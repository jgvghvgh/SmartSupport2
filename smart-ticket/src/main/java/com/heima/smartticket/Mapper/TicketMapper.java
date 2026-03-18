package com.heima.smartticket.Mapper;

import com.heima.smartcommon.DTO.TicketCreateDTO;
import com.heima.smartcommon.VO.TicketCreateVO;
import com.heima.smartticket.entity.Ticket;
import com.heima.smartticket.entity.TicketAttachment;
import com.heima.smartticket.entity.TicketMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TicketMapper {
    @Insert("insert into ticket(title,description,priority,user_id) values(#{title},#{description},#{priority},#{userId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    Long insert(Ticket ticketCreateDTO);
    @Select("SELECT * from ticket where user_id=#{userId} ORDER BY id DESC LIMIT #{pageNum}, #{pageSize}")
    List<TicketCreateVO> getTicket(Long userId, Integer pageNum, Integer pageSize);

    @Select("SELECT * from ticket where id= #{ticketId}")
    Ticket findById(Long ticketId);
    @Update("UPDATE ticket SET status = #{status}, assignee_id = #{assigneeId}, updated_at = NOW() WHERE id = #{id}")
    void update(Ticket ticket);
    @Select("SELECT COUNT(*) FROM ticket WHERE user_id = #{userId}")
    Long getTicketCount(Long userId);
    @Select("SELECT * from ticket where assignee_id=#{agentId} ORDER BY id DESC LIMIT #{pageNum}, #{pageSize}")
    List<TicketCreateVO> getAssignTicket(Long agentId, int offset, Integer pageSize);
    @Select("SELECT COUNT(*) FROM ticket WHERE ssignee_id=#{agentId}")
    Long getAssignTicketCount(Long agentId);
    @Insert("insert into ticket_attachment(ticket_id,file_name,file_url,uploader_id) values(#{ticketId},#{fileName},#{fileUrl},#{uploaderId} )")
    void insertAttachment(TicketAttachment ticketAttachment);

     @Select("SELECT * FROM ticket_attachment WHERE ticket_id = #{ticketId}")
    List<TicketAttachment> getAttachment(Long ticketId);



    List<TicketCreateVO> getTickets(List<Long> ticketIds);
    @Insert("insert into ticket_assign(ticket_id,agent_id) values(#{ticketId},#{agentId})")
    void insertAssignId(Long ticketId, Long agentId);
}
