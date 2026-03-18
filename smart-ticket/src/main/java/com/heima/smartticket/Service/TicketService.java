package com.heima.smartticket.Service;

import com.heima.smartcommon.DTO.TicketCreateDTO;
import com.heima.smartcommon.DTO.TicketMessageDTO;
import com.heima.smartcommon.Result.CommonResult;
import com.heima.smartcommon.Result.PageResult;
import com.heima.smartcommon.VO.TicketCreateVO;
import com.heima.smartticket.entity.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TicketService {
    CommonResult<String> submitTicket(TicketCreateDTO ticketCreateDTO);

    CommonResult<PageResult> getTicket(Long UserId, Integer pageNum, Integer pageSize);

    CommonResult<String> addMessage(TicketMessageDTO ticketMessage);

    CommonResult<String> Assign(Long ticketId);

    CommonResult<String> updateStatus(Long ticketId, String status);
    Boolean Messageexists(Long messageid);

    CommonResult<PageResult> getAssignTicket(Long agentId, Integer pageNum, Integer pageSize);

    CommonResult<String> PostTicketAttachment(Long ticketId, MultipartFile file);

    CommonResult<TicketVo> GetTicketAttachment(Long ticketId);

    CommonResult<List<TicketMessage>> GetComment(Long ticketId);

    CommonResult<List<TicketCreateVO>> GetTopTickets();

    CommonResult<TicketOverviewVO> overview();

    CommonResult<List <DailyTicketStatsVO>> daily();

    CommonResult<List<UserTicketRankVO>> topUsers();
}
