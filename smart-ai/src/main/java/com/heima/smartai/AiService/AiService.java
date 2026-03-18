package com.heima.smartai.AiService;

import com.heima.smartai.model.AiAnalysisResult;

public interface AiService {
    public AiAnalysisResult chat(String message, String ticketId);
}
