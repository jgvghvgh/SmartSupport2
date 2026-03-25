package com.heima.smartai.cache;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * RAG 检索结果缓存服务
 * 第二层缓存，缓存向量检索结果
 * 支持逻辑过期（异步刷新）和版本控制
 */
@Slf4j
@Service
public class RetrievalCacheService {

    private static final String PREFIX = "rag:retrieval:";
    private static final String VERSION_KEY = "rag:retrieval:version";
    private static final long EXPIRE_DAYS = 30;
    // 逻辑过期时间（提前触发异步刷新）
    private static final long LOGICAL_EXPIRE_HOURS = 24;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 获取缓存的检索结果
     * @return 检索到的文档列表，未命中或过期返回 null
     */
    public List<String> get(String queryHash) {
        String version = getVersion();
        String key = PREFIX + version + ":" + queryHash;

        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }

        try {
            JSONObject cacheEntry = JSON.parseObject(json);
            long createTime = cacheEntry.getLong("createTime");
            long expireTime = cacheEntry.getLong("expireTime");
            List<String> docs = cacheEntry.getJSONArray("docs").toList(String.class);

            // 检查逻辑过期
            long now = System.currentTimeMillis();
            if (now > expireTime) {
                log.debug("检索缓存逻辑过期, queryHash={}, createTime={}", queryHash, createTime);
                // 逻辑过期但数据还在，返回数据同时触发异步刷新（由 CacheWarmupService 统一处理）
                return docs;
            }

            return docs;
        } catch (Exception e) {
            log.warn("检索缓存解析失败, queryHash={}", queryHash, e);
            return null;
        }
    }

    /**
     * 检查缓存是否逻辑过期（用于判断是否需要异步刷新）
     */
    public boolean isLogicalExpired(String queryHash) {
        String version = getVersion();
        String key = PREFIX + version + ":" + queryHash;

        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return true;
        }

        try {
            JSONObject cacheEntry = JSON.parseObject(json);
            long expireTime = cacheEntry.getLong("expireTime");
            return System.currentTimeMillis() > expireTime;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 写入检索结果缓存（带逻辑过期）
     */
    public void set(String queryHash, List<String> docs) {
        String version = getVersion();
        String key = PREFIX + version + ":" + queryHash;

        long now = System.currentTimeMillis();
        long expireTime = now + LOGICAL_EXPIRE_HOURS * 60 * 60 * 1000L;

        JSONObject cacheEntry = new JSONObject();
        cacheEntry.put("docs", docs);
        cacheEntry.put("createTime", now);
        cacheEntry.put("expireTime", expireTime);
        cacheEntry.put("version", version);

        String json = cacheEntry.toJSONString();

        // 物理过期时间设置为更长，确保数据不丢失
        redisTemplate.opsForValue().set(key, json, EXPIRE_DAYS, TimeUnit.DAYS);
        log.debug("写入检索缓存, queryHash={}, docCount={}, logicalExpireHours={}",
                queryHash, docs.size(), LOGICAL_EXPIRE_HOURS);
    }

    /**
     * 版本变更时清除旧版本缓存
     */
    public void clearOldVersion(String oldVersion) {
        // 模糊匹配删除旧版本（生产环境建议用 scan）
        String pattern = PREFIX + oldVersion + ":*";
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("清除旧版本检索缓存, version={}, count={}", oldVersion, keys.size());
            }
        } catch (Exception e) {
            log.error("清除旧版本检索缓存失败, version={}", oldVersion, e);
        }
    }

    private String getVersion() {
        String version = redisTemplate.opsForValue().get(VERSION_KEY);
        if (version == null) {
            version = "v1";
            redisTemplate.opsForValue().set(VERSION_KEY, version);
        }
        return version;
    }
}
