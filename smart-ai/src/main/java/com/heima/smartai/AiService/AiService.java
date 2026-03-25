package com.heima.smartai.AiService;

import com.heima.smartai.model.AiAnalysisResult;

public interface AiService {
    AiAnalysisResult chat(String message, String ticketId);
    AiAnalysisResult chat(String message, String ticketId, String imageUrl, String imageType);
}
