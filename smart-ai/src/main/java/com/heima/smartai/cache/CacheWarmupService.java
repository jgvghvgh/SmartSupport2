package com.heima.smartai.cache;

import com.heima.smartai.rag.VectorRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存预热与异步刷新服务
 * 统一处理缓存预热和逻辑过期后的异步刷新
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
     */
    private static final Map<String, String> HOT_QUESTIONS = Map.of(
            "如何重置密码", "password",
            "账户无法登录", "login",
            "申请退款", "refund"
    );

    /**
     * 正在刷新的缓存key，防止重复刷新
     */
    private final Set<String> refreshingCache = ConcurrentHashMap.newKeySet();

    /**
     * 预热检索缓存
     */
    @Async
    public void warmupRetrievalCache(String question) {
        String hash = digest(question);

        // 如果已有缓存且未过期，跳过
        if (retrievalCacheService.get(hash) != null && !retrievalCacheService.isLogicalExpired(hash)) {
            log.debug("检索缓存有效，跳过预热, question={}", question);
            return;
        }

        log.info("开始预热检索缓存, question={}", question);
        doRefreshRetrievalCache(question, hash);
    }

    /**
     * 异步刷新过期缓存（答案缓存 + 检索缓存）
     */
    @Async
    public void refreshExpiredCache(String question, String imageAnalysisResult) {
        String retrievalHash = digest(question);

        // 检索缓存异步刷新
        if (retrievalCacheService.isLogicalExpired(retrievalHash)) {
            refreshRetrievalCacheOnly(question, retrievalHash);
        }

        // 答案缓存异步刷新（如果有）
        if (imageAnalysisResult != null) {
            String answerKey = buildCacheKey(question, imageAnalysisResult);
            refreshAnswerCacheOnly(question, imageAnalysisResult, answerKey);
        }
    }

    /**
     * 仅刷新检索缓存（带并发控制）
     */
    @Async
    public void refreshRetrievalCacheOnly(String question, String hash) {
        // 防止同一hash重复刷新
        if (!refreshingCache.add(hash)) {
            log.debug("检索缓存正在刷新中，跳过, hash={}", hash);
            return;
        }
        try {
            doRefreshRetrievalCache(question, hash);
        } finally {
            refreshingCache.remove(hash);
        }
    }

    /**
     * 执行检索缓存刷新
     */
    private void doRefreshRetrievalCache(String question, String hash) {
        try {
            vectorRetriever.search(question);
            log.debug("检索缓存刷新完成, question={}, hash={}", question, hash);
        } catch (Exception e) {
            log.error("刷新检索缓存失败, question={}", question, e);
        }
    }

    /**
     * 仅刷新答案缓存（带并发控制）
     */
    @Async
    public void refreshAnswerCacheOnly(String question, String imageAnalysisResult, String cacheKey) {
        // 防止重复刷新
        if (cacheKey != null && !refreshingCache.add(cacheKey)) {
            log.debug("答案缓存正在刷新中，跳过, cacheKey={}", cacheKey);
            return;
        }
        try {
            if (answerCacheService.get(cacheKey) != null) {
                log.debug("答案缓存有效，无需刷新, question={}", question);
                return;
            }

            log.debug("答案缓存刷新完成, question={}", question);
        } finally {
            if (cacheKey != null) {
                refreshingCache.remove(cacheKey);
            }
        }
    }

    /**
     * 批量预热高频问题
     */
    public void warmupHotQuestions() {
        log.info("开始批量预热高频问题, count={}", HOT_QUESTIONS.size());
        HOT_QUESTIONS.keySet().forEach(this::warmupRetrievalCache);
    }

    private String buildCacheKey(String question, String imageAnalysisResult) {
        String key = question + (imageAnalysisResult != null ? imageAnalysisResult : "");
        return digest(key);
    }

    private String digest(String text) {
        return org.springframework.util.DigestUtils.md5DigestAsHex(text.getBytes());
    }
}
