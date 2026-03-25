package com.heima.smartticket.Controller;

import com.heima.smartcommon.DTO.TicketCreateDTO;
import com.heima.smartcommon.DTO.TicketMessageDTO;
import com.heima.smartcommon.Result.CommonResult;
import com.heima.smartcommon.Result.PageResult;
import com.heima.smartcommon.VO.TicketCreateVO;
import com.heima.smartticket.Service.TicketService;
import com.heima.smartticket.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/ticket")
@EnableFeignClients
public class TicketController {
    @Autowired
    private TicketService ticketService;

    @PostMapping("/submitticket")
    public CommonResult<Long> submitTicket(@RequestBody TicketCreateDTO ticketCreateDTO){
        return ticketService.submitTicket(ticketCreateDTO);
    }

    @GetMapping("/getTicket")
    public CommonResult<PageResult> getTicket(@RequestParam Long UserId,
                                              @RequestParam(defaultValue = "1")  Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize){
        return ticketService.getTicket(UserId,pageNum,pageSize);
    }

    @PostMapping("/addMessage")
    public CommonResult<Integer> addMessage(@RequestBody TicketMessageDTO ticketMessage){
        return ticketService.addMessage(ticketMessage);
    }

    @PostMapping("/saveAiMessage")
    public CommonResult<Integer> saveAiMessage(@RequestParam Long ticketId, @RequestParam String content){
        return ticketService.saveAiMessage(ticketId, content);
    }

    @PutMapping("/Assign")
    public CommonResult<String> Assign(@RequestParam Long ticketId
                                  ){
        return ticketService.Assign(ticketId);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/updateStatus")
    public CommonResult<String> updateStatus(@RequestParam Long ticketId,
                                        @RequestParam String status){
        return ticketService.updateStatus(ticketId,status);
    }

    /**
     * 用户关闭工单
     */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/close")
    public CommonResult<String> closeTicket(@RequestParam Long ticketId){
        return ticketService.closeTicket(ticketId);
    }

    /**
     * 客服解决工单（标记为已解决）
     */
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    @PostMapping("/resolve")
    public CommonResult<String> resolveTicket(@RequestParam Long ticketId){
        return ticketService.resolveTicket(ticketId);
    }

    /**
     * 用户取消工单
     */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/cancel")
    public CommonResult<String> cancelTicket(@RequestParam Long ticketId){
        return ticketService.cancelTicket(ticketId);
    }

   @GetMapping("/getAssignTicket")
    public CommonResult<PageResult> getAssignTicket(@RequestParam Long agentId,
                                           @RequestParam(defaultValue = "1")  Integer pageNum,
                                           @RequestParam(defaultValue = "10") Integer pageSize){
        return ticketService.getAssignTicket(agentId,pageNum,pageSize);
    }
    @PostMapping("/PostTicketAttachment")
    public CommonResult<Long> PostTicketAttachment(@RequestParam Long ticketId,
                                                   @RequestParam MultipartFile file){
        return ticketService.PostTicketAttachment(ticketId,file);
    }
    @GetMapping("/GetTicketDetails")
    public CommonResult<TicketVo> GetTicketAttachment(@RequestParam Long ticketId){
        return ticketService.GetTicketAttachment(ticketId);
    }
    @GetMapping("/GetComment")
    public CommonResult<List<TicketMessage>> GetComment(@RequestParam Long ticketId){
        return ticketService.GetComment(ticketId);
    }

    @GetMapping("/GetTopTickets")
    public CommonResult<List<TicketCreateVO>> GetTopTickets(){
        return ticketService.GetTopTickets();
    }
    @GetMapping("overview")
    public CommonResult<TicketOverviewVO> overview(){
        return ticketService.overview();
    }
    @GetMapping("daily")
    public CommonResult<List<DailyTicketStatsVO> >daily(){
        return ticketService.daily();
    }
    @GetMapping("top-users")
    public CommonResult<List<UserTicketRankVO>> topUsers(){
        return ticketService.topUsers();
    }
}
