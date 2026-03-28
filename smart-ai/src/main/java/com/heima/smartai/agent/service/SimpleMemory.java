package com.heima.smartai.agent.service;

import com.heima.smartai.entity.AgentChatMessage;
import com.heima.smartai.mapper.AgentChatMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 对话记忆 - Redis + 数据库双写双读兜底
 *
 * 读：优先Redis，Redis无数据则查数据库
 * 写：双写，Redis + 数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimpleMemory {

    private static final String PREFIX = "agent:memory:";
    private static final long REDIS_EXPIRE_HOURS = 24;
    private static final int MAX_REDIS_HISTORY = 20;

    private final StringRedisTemplate redisTemplate;
    private final AgentChatMessageMapper chatMessageMapper;

    public void addUserMessage(String ticketId, String message) {
        addMessage(ticketId, "user", message);
    }

    public void addAssistantMessage(String ticketId, String message) {
        addMessage(ticketId, "assistant", message);
    }

    /**
     * 获取对话历史 - 优先Redis，兜底数据库
     */
    public List<ChatMessage> getHistory(String ticketId) {
        // 1. 尝试从Redis获取
        List<ChatMessage> redisHistory = getHistoryFromRedis(ticketId);
        if (!redisHistory.isEmpty()) {
            return redisHistory;
        }

        // 2. Redis没有，查数据库
        List<ChatMessage> dbHistory = getHistoryFromDb(ticketId);
        if (!dbHistory.isEmpty()) {
            // 3. 回填Redis
            fillRedisFromDb(ticketId, dbHistory);
            return dbHistory;
        }

        return new ArrayList<>();
    }

    /**
     * 清空对话历史（Redis + 数据库）
     */
    public void clear(String ticketId) {
        redisTemplate.delete(PREFIX + ticketId);
        try {
            chatMessageMapper.deleteByTicketId(ticketId);
        } catch (Exception e) {
            log.warn("清空数据库历史失败, ticketId={}", ticketId, e);
        }
    }

    /**
     * 格式化对话历史为字符串
     */
    public String getFormattedHistory(String ticketId) {
        List<ChatMessage> history = getHistory(ticketId);
        if (history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : history) {
            String role = "user".equals(msg.role) ? "用户" : "助手";
            sb.append(role).append("：").append(msg.content()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 判断是否是新对话（Redis + 数据库都无数据）
     */
    public boolean isNewConversation(String ticketId) {
        // 先查Redis
        if (!getHistoryFromRedis(ticketId).isEmpty()) {
            return false;
        }
        // 再查数据库
        return chatMessageMapper.countByTicketId(ticketId) == 0;
    }

    /**
     * 获取历史记录数量（Redis + 数据库合并计算）
     */
    public int getHistoryCount(String ticketId) {
        int redisCount = getHistoryFromRedis(ticketId).size();
        if (redisCount >= MAX_REDIS_HISTORY) {
            return redisCount;
        }
        Long dbCount = chatMessageMapper.countByTicketId(ticketId);
        return Math.max(redisCount, dbCount.intValue());
    }

    // ==================== 私有方法 ====================

    private void addMessage(String ticketId, String role, String content) {
        // 1. 先写数据库（持久化）
        saveToDb(ticketId, role, content);

        // 2. 再更新Redis
        addToRedis(ticketId, role, content);
    }

    private void saveToDb(String ticketId, String role, String content) {
        try {
            AgentChatMessage message = AgentChatMessage.builder()
                    .ticketId(ticketId)
                    .role(role)
                    .content(content)
                    .createdAt(LocalDateTime.now())
                    .build();
            chatMessageMapper.insert(message);
        } catch (Exception e) {
            log.error("保存对话历史到数据库失败, ticketId={}, role={}", ticketId, role, e);
        }
    }

    private void addToRedis(String ticketId, String role, String content) {
        try {
            String key = PREFIX + ticketId;
            List<ChatMessage> history = getHistoryFromRedis(ticketId);
            history.add(new ChatMessage(role, content, System.currentTimeMillis()));

            // 限制Redis中的条数
            if (history.size() > MAX_REDIS_HISTORY) {
                history = history.subList(history.size() - MAX_REDIS_HISTORY, history.size());
            }

            String json = com.alibaba.fastjson2.JSON.toJSONString(history);
            redisTemplate.opsForValue().set(key, json, REDIS_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("更新Redis对话历史失败, ticketId={}, role={}", ticketId, role, e);
        }
    }

    private List<ChatMessage> getHistoryFromRedis(String ticketId) {
        try {
            String key = PREFIX + ticketId;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            return com.alibaba.fastjson2.JSON.parseArray(json, ChatMessage.class);
        } catch (Exception e) {
            log.warn("读取Redis历史失败, ticketId={}", ticketId, e);
            return new ArrayList<>();
        }
    }

    private List<ChatMessage> getHistoryFromDb(String ticketId) {
        try {
            List<AgentChatMessage> dbMessages = chatMessageMapper.findByTicketId(ticketId);
            return dbMessages.stream()
                    .map(m -> new ChatMessage(m.getRole(), m.getContent(),
                            m.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("读取数据库历史失败, ticketId={}", ticketId, e);
            return new ArrayList<>();
        }
    }

    private void fillRedisFromDb(String ticketId, List<ChatMessage> dbHistory) {
        try {
            // 只取最近的MAX_REDIS_HISTORY条回填Redis
            List<ChatMessage> toCache = dbHistory.size() > MAX_REDIS_HISTORY
                    ? dbHistory.subList(dbHistory.size() - MAX_REDIS_HISTORY, dbHistory.size())
                    : dbHistory;

            String key = PREFIX + ticketId;
            String json = com.alibaba.fastjson2.JSON.toJSONString(toCache);
            redisTemplate.opsForValue().set(key, json, REDIS_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("回填Redis失败, ticketId={}", ticketId, e);
        }
    }

    public record ChatMessage(String role, String content, long timestamp) {}
}
