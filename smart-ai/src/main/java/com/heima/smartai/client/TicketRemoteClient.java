package com.heima.smartai.client;


import com.heima.smartai.model.TicketContent;
import com.heima.smartcommon.Result.CommonResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ticket-service") // ticket模块在注册中心的服务名
public interface TicketRemoteClient {

    @GetMapping("/api/ticket/GetTicketDetails")
    CommonResult<TicketContent> getTicketAttachment(@RequestParam("ticketId") Long ticketId);

    @PostMapping("/api/ticket/saveAiMessage")
    CommonResult<String> saveAiMessage(@RequestParam("ticketId") Long ticketId, @RequestParam("content") String content);
}
