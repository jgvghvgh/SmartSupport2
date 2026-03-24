package com.heima.smartai.Controller;

import com.heima.smartai.AiService.AiService;
import com.heima.smartai.model.AiAnalysisResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    @Autowired
    private AiService aiService;
    @PostMapping("/chat")
    public AiAnalysisResult chat(@RequestParam("message") String message,
                                  @RequestParam("ticketId") String ticketId){
        return aiService.chat(message, ticketId);
    }
}
