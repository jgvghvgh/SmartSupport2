package com.heima.smartai.cache;

import com.heima.smartai.model.AiAnalysisResult;
import com.heima.smartai.rag.VectorRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存预热服务
 * 支持高频问题预存和异步刷新
 */
@Slf4j
@Service
public class CacheWarmupService {

    @Autowired
    private AnswerCacheService answerCacheService;

    @Autowired
    private RetrievalCacheService retrievalCacheService;

    @Autowired
    private VectorRetriever vectorRetriever;

    /**
     * 高频问题列表（可配置化）
     * 格式：question -> expected_answer_pattern
     */
    private static final Map<String, String> HOT_QUESTIONS = Map.of(
            "如何重置密码", "password",
            "账户无法登录", "login",
            "申请退款", "refund"
    );

    /**
     * 预热答案缓存
     */
    @Async
    public void warmupAnswerCache(String question, String imageAnalysisResult) {
        String cacheKey = buildCacheKey(question, imageAnalysisResult);

        // 如果已有缓存，跳过
        if (answerCacheService.get(cacheKey) != null) {
            log.debug("答案缓存已存在，跳过预热, question={}", question);
            return;
        }

        log.info("开始预热答案缓存, question={}", question);
        // 注意：这里需要实际调用 AI，简化处理
    }

    /**
     * 预热检索缓存
     */
    @Async
    public void warmupRetrievalCache(String question) {
        String hash = digest(question);

        // 如果已有缓存，跳过
        if (retrievalCacheService.get(hash) != null) {
            log.debug("检索缓存已存在，跳过预热, question={}", question);
            return;
        }

        log.info("开始预热检索缓存, question={}", question);
        // 实际执行向量检索并缓存
        try {
            vectorRetriever.search(question);
        } catch (Exception e) {
            log.error("预热检索缓存失败, question={}", question, e);
        }
    }

    /**
     * 批量预热高频问题
     */
    public void warmupHotQuestions() {
        log.info("开始批量预热高频问题, count={}", HOT_QUESTIONS.size());
        HOT_QUESTIONS.keySet().forEach(this::warmupRetrievalCache);
    }

    /**
     * 异步刷新过期缓存
     */
    @Async
    public void refreshExpiredCache(String question, String imageAnalysisResult) {
        String answerCacheKey = buildCacheKey(question, imageAnalysisResult);
        String retrievalCacheKey = digest(question);

        // 检查答案缓存
        if (answerCacheService.get(answerCacheKey) == null) {
            log.debug("答案缓存为空，无需刷新, question={}", question);
            return;
        }

        // 检查检索缓存是否过期
        if (!retrievalCacheService.isLogicalExpired(retrievalCacheKey)) {
            log.debug("检索缓存未过期，无需刷新, question={}", question);
            return;
        }

        log.info("刷新过期缓存, question={}", question);

        // 先刷新检索缓存
        try {
            vectorRetriever.search(question);
        } catch (Exception e) {
            log.error("刷新检索缓存失败, question={}", question, e);
        }
    }

    private String buildCacheKey(String question, String imageAnalysisResult) {
        String key = question + (imageAnalysisResult != null ? imageAnalysisResult : "");
        return digest(key);
    }

    private String digest(String text) {
        return org.springframework.util.DigestUtils.md5DigestAsHex(text.getBytes());
    }
}
