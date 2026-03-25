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
    CommonResult<Long> submitTicket(TicketCreateDTO ticketCreateDTO);

    CommonResult<PageResult> getTicket(Long UserId, Integer pageNum, Integer pageSize);

    CommonResult<Integer> addMessage(TicketMessageDTO ticketMessage);

    CommonResult<Integer> saveAiMessage(Long ticketId, String content);

    CommonResult<String> Assign(Long ticketId);

    CommonResult<String> updateStatus(Long ticketId, String status);

    /**
     * 用户关闭工单
     * @param ticketId 工单ID
     * @return 操作结果
     */
    CommonResult<String> closeTicket(Long ticketId);

    /**
     * 客服解决工单（标记为已解决）
     * @param ticketId 工单ID
     * @return 操作结果
     */
    CommonResult<String> resolveTicket(Long ticketId);

    /**
     * 用户取消工单
     * @param ticketId 工单ID
     * @return 操作结果
     */
    CommonResult<String> cancelTicket(Long ticketId);

    void updateStatusByBusinessAction(Long ticketId, String senderType, String messageContent);
    Boolean Messageexists(Long messageid);

    CommonResult<PageResult> getAssignTicket(Long agentId, Integer pageNum, Integer pageSize);

    CommonResult<Long> PostTicketAttachment(Long ticketId, MultipartFile file);

    CommonResult<TicketVo> GetTicketAttachment(Long ticketId);

    CommonResult<List<TicketMessage>> GetComment(Long ticketId);

    CommonResult<List<TicketCreateVO>> GetTopTickets();

    CommonResult<TicketOverviewVO> overview();

    CommonResult<List <DailyTicketStatsVO>> daily();

    CommonResult<List<UserTicketRankVO>> topUsers();
}
