package com.heima.smartai.AiService;

import com.heima.smartai.Config.AiClient;
import com.heima.smartai.cache.AnswerCacheService;
import com.heima.smartai.cache.CacheWarmupService;
import com.heima.smartai.cache.RetrievalCacheService;
import com.heima.smartai.client.TicketRemoteClient;
import com.heima.smartai.intent.IntentClassifierService;
import com.heima.smartai.model.AiAnalysisResult;
import com.heima.smartai.model.Message;
import com.heima.smartai.model.TicketContent;
import com.heima.smartai.rag.PromptBuilder;
import com.heima.smartai.rag.QueryRewriteService;
import com.heima.smartai.rag.RerankService;
import com.heima.smartai.rag.VectorRetriever;

import com.heima.smartcommon.Result.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 聊天服务实现
 * 负责路由判断、缓存命中和逻辑链路编排
 */
@Slf4j
@Service
@Primary
public class AiServiceImpl implements AiService {

    @Autowired
    private TicketRemoteClient ticketRemoteClient;

    @Autowired
    private IntentClassifierService intentClassifierService;

    @Autowired
    private AiClient aiClient;

    @Autowired
    private VectorRetriever vectorRetriever;

    @Autowired
    private RerankService rerankService;

    @Autowired
    private QueryRewriteService queryRewriteService;

    @Autowired
    private AnswerCacheService answerCacheService;

    @Autowired
    private RetrievalCacheService retrievalCacheService;

    @Autowired
    private CacheWarmupService cacheWarmupService;

    @Autowired
    private ImageRecognitionService imageRecognitionService;

    @Override
    public AiAnalysisResult chat(String message, String ticketId) {
        return chat(message, ticketId, null, null);
    }

    @Override
    public AiAnalysisResult chat(String message, String ticketId, String imageUrl, String imageType) {
        // 1. 获取工单内容
        TicketContent ticket = getTicketContent(ticketId);
        String question = (message == null || message.isBlank())
                ? ticket.getContent()
                : message;

        // 2. 图片识别（如果提供了图片）
        String imageAnalysisResult = recognizeImageIfNeeded(imageUrl, imageType, ticketId);

        // 3. 构建答案缓存 key
        String answerCacheKey = buildCacheKey(question, imageAnalysisResult);

        // 4. 查询答案缓存（第一层缓存）
        AiAnalysisResult cachedResult = answerCacheService.get(answerCacheKey);
        if (cachedResult != null) {
            log.info("答案缓存命中, ticketId={}", ticketId);
            // 异步刷新过期缓存
            cacheWarmupService.refreshExpiredCache(question, imageAnalysisResult);
            return cachedResult;
        }

        // 5. 意图分类
        IntentClassifierService.IntentResult intent =
                intentClassifierService.classify(question, ticket.getSenderId());

        if (!intent.ok) {
            AiAnalysisResult result = new AiAnalysisResult("意图识别失败", intent.reason);
            saveSummary(ticketId, result);
            return result;
        }

        // 6. 路由判断
        AiAnalysisResult result = route(intent, question, imageAnalysisResult);

        // 7. 保存结果并写入答案缓存
        saveSummary(ticketId, result);
        answerCacheService.set(answerCacheKey, result);

        return result;
    }

    /**
     * 路由判断
     * - OTHER/outer: 直接调用 AI chat
     * - FAQ: 走 RAG 流程
     * - NEED_MORE_INFO: 返回需要补充信息
     */
    private AiAnalysisResult route(IntentClassifierService.IntentResult intent,
                                    String question, String imageAnalysisResult) {
        String intentType = intent.intent;

        if ("NEED_MORE_INFO".equalsIgnoreCase(intentType)) {
            return new AiAnalysisResult(intent.reason, "请客服补充/请重新描述");
        }

        if ("OTHER".equalsIgnoreCase(intentType)) {
            // outer 类型：直接走 AI chat，不走 RAG
            log.debug("outer意图，直接调用AI聊天");
            return chatWithAi(question, imageAnalysisResult);
        }

        // FAQ 类型：走 RAG 流程
        log.debug("FAQ意图，走RAG流程");
        return chatWithRag(question, imageAnalysisResult);
    }

    /**
     * 直接 AI 聊天（outer 类型）
     */
    private AiAnalysisResult chatWithAi(String question, String imageAnalysisResult) {
        String prompt = buildDirectPrompt(question, imageAnalysisResult);
        Message msg = Message.ofUser(prompt);
        String aiText = aiClient.chat(List.of(msg));
        return aiClient.parseResponse(aiText);
    }

    /**
     * RAG 流程（FAQ 类型）
     * 包含第二层缓存：检索结果缓存
     */
    private AiAnalysisResult chatWithRag(String question, String imageAnalysisResult) {
        // 0. Query Rewrite
        String rewriteQuery = queryRewriteService.rewrite(question);
        String rewriteCacheKey = digest(rewriteQuery);

        // 1. 查询检索缓存（第二层缓存）
        List<String> docs = retrievalCacheService.get(rewriteCacheKey);
        if (docs != null && !docs.isEmpty()) {
            log.debug("检索缓存命中, rewriteQuery={}", rewriteQuery);
            // 检查是否逻辑过期，异步刷新
            if (retrievalCacheService.isLogicalExpired(rewriteCacheKey)) {
                cacheWarmupService.warmupRetrievalCache(rewriteQuery);
            }
        } else {
            // 2. 向量检索（包含写入缓存）
            docs = vectorRetriever.search(rewriteQuery);
        }

        // 3. Rerank
        List<String> topDocs = rerankService.rerank(rewriteQuery, docs);

        // 4. Build Prompt
        String prompt = PromptBuilder.build(question, topDocs, imageAnalysisResult);

        // 5. AI Chat
        Message msg = Message.ofUser(prompt);
        String aiText = aiClient.chat(List.of(msg));

        return aiClient.parseResponse(aiText);
    }

    /**
     * 构建直接回复的 prompt（不走 RAG）
     */
    private String buildDirectPrompt(String question, String imageAnalysisResult) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个客服专家，请根据用户问题直接回答。\n\n");
        prompt.append("用户问题：\n").append(question).append("\n\n");
        if (imageAnalysisResult != null && !imageAnalysisResult.isBlank()) {
            prompt.append("用户上传的图片内容：\n").append(imageAnalysisResult).append("\n\n");
        }
        prompt.append("请回答：\n1. 问题分析\n2. 解决建议");
        return prompt.toString();
    }

    private TicketContent getTicketContent(String ticketId) {
        CommonResult<TicketContent> result = ticketRemoteClient.getTicketAttachment(Long.valueOf(ticketId));
        if (result == null || result.getData() == null) {
            throw new RuntimeException("Ticket not found: " + ticketId);
        }
        return result.getData();
    }

    private String recognizeImageIfNeeded(String imageUrl, String imageType, String ticketId) {
        if (imageUrl == null || imageUrl.isBlank() || imageRecognitionService == null) {
            return null;
        }
        if (!imageRecognitionService.isImageFile(imageType)) {
            return null;
        }
        try {
            String result = imageRecognitionService.recognizeImageContent(imageUrl, imageType);
            log.info("图片识别完成, ticketId={}, result={}", ticketId, result);
            return result;
        } catch (Exception e) {
            log.error("图片识别失败, ticketId={}, error={}", ticketId, e.getMessage());
            return null;
        }
    }

    private String buildCacheKey(String question, String imageAnalysisResult) {
        String key = question + (imageAnalysisResult != null ? imageAnalysisResult : "");
        return digest(key);
    }

    private String digest(String text) {
        return org.springframework.util.DigestUtils.md5DigestAsHex(text.getBytes());
    }

    private void saveSummary(String ticketId, AiAnalysisResult result) {
        String content = "【问题分析】" + result.getProblemAnalysis() + "\n\n" + "【参考回复】" + result.getReferenceReply();
        ticketRemoteClient.saveAiMessage(Long.valueOf(ticketId), content);
    }
}
