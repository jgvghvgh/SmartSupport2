package com.heima.smartai.AiService;

import com.heima.smartai.Config.AiClient;
import com.heima.smartai.intent.IntentClassifierService;
import com.heima.smartai.client.TicketRemoteClient;
import com.heima.smartai.mapper.AiMapper;
import com.heima.smartai.model.AiAnalysisResult;
import com.heima.smartai.model.TicketContent;
import com.heima.smartai.model.TicketSummary;
import com.heima.smartai.rag.PromptBuilder;
import com.heima.smartai.rag.QueryRewriteService;
import com.heima.smartai.rag.RerankService;
import com.heima.smartai.rag.VectorRetriever;
import com.heima.smartcommon.Result.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class Aiserviceimpl implements AiService {
    @Autowired
    private TicketRemoteClient ticketRemoteClient;
    @Autowired
    private AiMapper ticketSummaryMapper;
    @Autowired
    private QueryRewriteService queryRewriteService;
    @Autowired
    private IntentClassifierService intentClassifierService;
    @Autowired
    private AiClient aiClient;
    @Autowired
    private VectorRetriever vectorRetriever;
    @Autowired
    private RerankService rerankService;
    @Autowired(required = false)
    private ImageRecognitionService imageRecognitionService;

    @Override
    public AiAnalysisResult chat(String message, String ticketId) {
        return chat(message, ticketId, null, null);
    }

    @Override
    public AiAnalysisResult chat(String message, String ticketId, String imageUrl, String imageType) {
        CommonResult<TicketContent> result = ticketRemoteClient.getTicketAttachment(Long.valueOf(ticketId));
        if (result == null || result.getData() == null) {
            throw new RuntimeException("Ticket not found: " + ticketId);
        }
        TicketContent ticket = (TicketContent) result.getData();
        String question = (message == null || message.isBlank())
                ? ticket.getContent()
                : message;

        String imageAnalysisResult = null;
        if (imageUrl != null && !imageUrl.isBlank() && imageRecognitionService != null) {
            try {
                if (isImageFile(imageType)) {
                    imageAnalysisResult = imageRecognitionService.recognizeImageContent(imageUrl, imageType);
                    log.info("图片识别完成, ticketId={}, result={}", ticketId, imageAnalysisResult);
                }
            } catch (Exception e) {
                log.error("图片识别失败, ticketId={}, error={}", ticketId, e.getMessage());
            }
        }

        IntentClassifierService.IntentResult intent =
                intentClassifierService.classify(question, ticket.getSenderId());

        if (!intent.ok) {
            AiAnalysisResult fallback = new AiAnalysisResult("意图识别失败", "请客服补充/请重新描述");
            saveSummary(ticketId, fallback);
            return fallback;
        }

        if ("NEED_MORE_INFO".equalsIgnoreCase(intent.intent)) {
            AiAnalysisResult fallback = new AiAnalysisResult(intent.reason, "请客服补充/请重新描述");
            saveSummary(ticketId, fallback);
            return fallback;
        }

        if ("OTHER".equalsIgnoreCase(intent.intent)) {
            return chat(message, ticketId, imageUrl, imageType);
        }

        String rewriteQuery = queryRewriteService.rewrite(question);
        List<String> docs = vectorRetriever.search(rewriteQuery);
        List<String> topDocs = rerankService.rerank(rewriteQuery, docs);

        String prompt = "FAQ".equalsIgnoreCase(intent.intent)
                ? PromptBuilder.build(question, topDocs, imageAnalysisResult)
                : PromptBuilder.buildCautious(question, topDocs, imageAnalysisResult);

        List<Map<String, Object>> messages = buildMessages(prompt);
        AiAnalysisResult analysisResult = aiClient.chat(messages);

        saveSummary(ticketId, analysisResult);
        return analysisResult;
    }

    private boolean isImageFile(String fileType) {
        if (fileType == null) return false;
        return fileType.toLowerCase().startsWith("image/");
    }

    private void saveSummary(String ticketId, AiAnalysisResult result) {
        TicketSummary summary = new TicketSummary();
        summary.setTicketId(Long.valueOf(ticketId));
        summary.setAiSummary("【问题分析】" + result.getProblemAnalysis() + "\n\n" + "【参考回复】" + result.getReferenceReply());
        summary.setSatisfactionScore((short) 0);
        summary.setCreatedAt(LocalDateTime.now());
        ticketSummaryMapper.insert(summary);
    }

    private List<Map<String, Object>> buildMessages(String prompt) {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", prompt);
        messages.add(msg);
        return messages;
    }
}
