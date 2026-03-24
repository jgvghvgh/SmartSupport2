package com.heima.smartai.AiService;

import com.heima.smartai.Config.AIConfig;
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
import com.heima.smartticket.entity.TicketAttachment;
import org.apache.ibatis.annotations.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class Aiserviceimpl implements AiService{
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
    @Override
    public AiAnalysisResult chat(String message, String ticketId) {
        //  1. 获取工单信息
        CommonResult<TicketContent> result = ticketRemoteClient.getTicketAttachment(Long.valueOf(ticketId));
        if (result == null || result.getData() == null) {
            throw new RuntimeException("Ticket not found: " + ticketId);
        }
        TicketContent ticket =(TicketContent) result.getData();
        // 优先使用用户首次 addMessage 内容；兜底使用工单描述
        String question = (message == null || message.isBlank())
                ? ticket.getContent()
                : message;

        // 2 意图分类分流（LLM + 分类缓存 + 分类限流）
        IntentClassifierService.IntentResult intent =
                intentClassifierService.classify(question, ticket.getSenderId());

        // 识别失败：直接返回兜底文案
        if (!intent.ok) {
            AiAnalysisResult fallback = new AiAnalysisResult("意图识别失败", "请客服补充/请重新描述");
            saveSummary(ticketId, fallback);
            return fallback;
        }

        if ("NEED_MORE_INFO".equalsIgnoreCase(intent.intent)) {
            AiAnalysisResult fallback =
                    new AiAnalysisResult(intent.reason, "请客服补充/请重新描述");
            saveSummary(ticketId, fallback);
            return fallback;
        }

        if("OTHER".equalsIgnoreCase(intent.intent)){

            return chat(message, ticketId);
        }
        // 3 QueryRewrite
        String rewriteQuery = queryRewriteService.rewrite(question);

        // 2 Vector Search (embedding + search)
        List<String> docs =
                vectorRetriever.search(rewriteQuery);

        // 3 Rerank
        List<String> topDocs =
                rerankService.rerank(rewriteQuery,docs);

        // 4 Prompt（根据意图做路由/差异化提示）
        String prompt = "FAQ".equalsIgnoreCase(intent.intent)
                ? PromptBuilder.build(question, topDocs)
                : PromptBuilder.buildCautious(question, topDocs);

        // 5 AI
        List<Map<String,Object>> messages =
                buildMessages(prompt);
        AiAnalysisResult analysisResult = aiClient.chat(messages);

        saveSummary(ticketId, analysisResult);

        return analysisResult;
    }
    // 封装：调用 AI 的逻辑（上面写好的）


    // 格式化存储内容
    private String formatAiSummary(AiAnalysisResult result) {
        return "【问题分析】" + result.getProblemAnalysis() + "\n\n" +
                "【参考回复】" + result.getReferenceReply();
    }

    private void saveSummary(String ticketId, AiAnalysisResult result) {
        // 保存结果到数据库（用于面试讲“智能客服闭环”）
        TicketSummary summary = new TicketSummary();
        summary.setTicketId(Long.valueOf(ticketId));
        summary.setAiSummary(formatAiSummary(result));
        summary.setSatisfactionScore((short) 0); // 默认 0，后续用户可打分
        summary.setCreatedAt(LocalDateTime.now());
        ticketSummaryMapper.insert(summary);
    }

    private List<Map<String,Object>> buildMessages(String prompt){

        List<Map<String,Object>> messages =
                new ArrayList<>();

        Map<String,Object> msg =
                new HashMap<>();

        msg.put("role","user");
        msg.put("content",prompt);

        messages.add(msg);

        return messages;
    }
}
