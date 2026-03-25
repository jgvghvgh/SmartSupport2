package com.heima.smartai.cache;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.heima.smartai.model.AiAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * AI 答案缓存服务
 * 第一层缓存，缓存最终答案
 */
@Slf4j
@Service
public class AnswerCacheService {

    private static final String PREFIX = "ai:answer:cache:";
    private static final long EXPIRE_DAYS = 7;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 获取缓存的聊天结果
     */
    public AiAnalysisResult get(String cacheKey) {
        String key = PREFIX + cacheKey;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(json, AiAnalysisResult.class);
        } catch (Exception e) {
            log.warn("答案缓存解析失败, key={}", cacheKey, e);
            return null;
        }
    }

    /**
     * 写入聊天结果缓存
     */
    public void set(String cacheKey, AiAnalysisResult result) {
        String key = PREFIX + cacheKey;
        String json = JSON.toJSONString(result);
        redisTemplate.opsForValue().set(key, json, EXPIRE_DAYS, TimeUnit.DAYS);
    }

    /**
     * 删除缓存
     */
    public void delete(String cacheKey) {
        String key = PREFIX + cacheKey;
        redisTemplate.delete(key);
    }
}
