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
    public CommonResult<String> submitTicket(@RequestBody TicketCreateDTO ticketCreateDTO){
        return ticketService.submitTicket(ticketCreateDTO);
    }

    @GetMapping("/getTicket")
    public CommonResult<PageResult> getTicket(@RequestParam Long UserId,
                                              @RequestParam(defaultValue = "1")  Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize){
        return ticketService.getTicket(UserId,pageNum,pageSize);
    }

    @PostMapping("/addMessage")
    public CommonResult<String> addMessage(@RequestBody TicketMessageDTO ticketMessage){
        return ticketService.addMessage(ticketMessage);
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
   @GetMapping("/getAssignTicket")
    public CommonResult<PageResult> getAssignTicket(@RequestParam Long agentId,
                                           @RequestParam(defaultValue = "1")  Integer pageNum,
                                           @RequestParam(defaultValue = "10") Integer pageSize){
        return ticketService.getAssignTicket(agentId,pageNum,pageSize);
    }
    @PostMapping("/PostTicketAttachment")
    public CommonResult<String> PostTicketAttachment(@RequestParam Long ticketId,
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
