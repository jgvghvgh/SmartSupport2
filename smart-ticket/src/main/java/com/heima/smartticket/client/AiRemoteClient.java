package com.heima.smartticket.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "smart-ai")
public interface AiRemoteClient {

    @PostMapping("/api/ai/chat")
    AiAnalysisResultResponse chat(
            @RequestParam("message") String message,
            @RequestParam("ticketId") String ticketId,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestParam(value = "imageType", required = false) String imageType
    );
}
