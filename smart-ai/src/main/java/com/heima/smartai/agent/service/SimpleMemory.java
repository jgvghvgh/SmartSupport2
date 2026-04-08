package com.heima.smartai.agent.service;

import com.heima.smartai.entity.TicketMessage;
import com.heima.smartai.mapper.TicketMessageMapper;
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
 * 对话记忆服务 - Redis + 数据库两层缓存
 *
 * 读：优先 Redis，Redis 无数据则查数据库
 * 写：双写，Redis（短期记忆）+ 数据库（持久化）
 *
 * Redis 只缓存最近的 N 条，数据库保存完整历史
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimpleMemory {

    private static final String REDIS_KEY_PREFIX = "agent:memory:";
    private static final long REDIS_EXPIRE_HOURS = 24;
    /** Redis 缓存的最大消息条数（双向各 N 条 = 共 2N 条） */
    private static final int MAX_REDIS_MESSAGES = 20;

    private final StringRedisTemplate redisTemplate;
    private final TicketMessageMapper ticketMessageMapper;



    /**
     * 添加用户消息
     */
    public void addUserMessage(Long ticketId, String content) {
        addMessage(ticketId, TicketMessage.SENDER_USER, content, false);
    }

    /**
     * 添加助手消息
     */
    public void addAssistantMessage(Long ticketId, String content) {
        addMessage(ticketId, TicketMessage.SENDER_AI, content, true);
    }

    /**
     * 添加任意类型消息
     * @param ticketId 工单ID
     * @param role 角色：user / assistant / observation / system
     * @param content 消息内容
     */
    public void addMessage(Long ticketId, String role, String content) {
        String senderType = TicketMessage.toSenderType(role);
        boolean isAi = TicketMessage.isAiMessage(role);
        addMessage(ticketId, senderType, content, isAi);
    }

    /**
     * 获取对话历史（合并 Redis + 数据库，Redis 优先）
     * @return 按时间正序排列的消息列表
     */
    public List<ChatMessage> getHistory(Long ticketId) {
        // 1. 优先从 Redis 获取
        List<ChatMessage> redisHistory = getFromRedis(ticketId);
        if (!redisHistory.isEmpty()) {
            return redisHistory;
        }

        // 2. Redis 没有，查数据库
        List<ChatMessage> dbHistory = getFromDb(ticketId);
        if (!dbHistory.isEmpty()) {
            // 3. 回填 Redis
            fillRedis(ticketId, dbHistory);
            return dbHistory;
        }

        return new ArrayList<>();
    }

    /**
     * 判断是否是新对话（Redis + 数据库都无数据）
     */
    public boolean isNewConversation(Long ticketId) {
        if (!getFromRedis(ticketId).isEmpty()) {
            return false;
        }
        return ticketMessageMapper.countByTicketId(ticketId) == 0;
    }

    /**
     * 格式化对话历史为字符串（用于注入 LLM 上下文）
     */
    public String getFormattedHistory(Long ticketId) {
        List<ChatMessage> history = getHistory(ticketId);
        if (history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : history) {
            String role = TicketMessage.SENDER_USER.equals(msg.senderType()) ? "用户" : "助手";
            sb.append(role).append("：").append(msg.content()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 清空对话历史（Redis + 数据库）
     */
    public void clear(Long ticketId) {
        redisTemplate.delete(REDIS_KEY_PREFIX + ticketId);
        try {
            ticketMessageMapper.deleteByTicketId(ticketId);
        } catch (Exception e) {
            log.warn("清空数据库历史失败, ticketId={}", ticketId, e);
        }
    }

    // ==================== 内部实现 ====================

    private void addMessage(Long ticketId, String senderType, String content, boolean isAi) {
        // 1. 先写数据库（持久化）
        saveToDb(ticketId, senderType, content, isAi);
        // 2. 再更新 Redis（短期缓存）
        addToRedis(ticketId, senderType, content, isAi);
    }

    private void saveToDb(Long ticketId, String senderType, String content, boolean isAi) {
        try {
            TicketMessage message = TicketMessage.builder()
                    .ticketId(ticketId)
                    .senderType(senderType)
                    .content(content)
                    .isAi(isAi)
                    .createdAt(LocalDateTime.now())
                    .build();
            ticketMessageMapper.insert(message);
        } catch (Exception e) {
            log.error("保存消息到数据库失败, ticketId={}", ticketId, e);
        }
    }

    private void addToRedis(Long ticketId, String senderType, String content, boolean isAi) {
        try {
            String key = REDIS_KEY_PREFIX + ticketId;
            List<ChatMessage> history = getFromRedis(ticketId);

            history.add(new ChatMessage(senderType, content, isAi, System.currentTimeMillis()));

            // 限制 Redis 条数（保留最近 MAX_REDIS_MESSAGES 条）
            if (history.size() > MAX_REDIS_MESSAGES) {
                history = history.subList(history.size() - MAX_REDIS_MESSAGES, history.size());
            }

            String json = com.alibaba.fastjson2.JSON.toJSONString(history);
            redisTemplate.opsForValue().set(key, json, REDIS_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("更新Redis记忆失败, ticketId={}", ticketId, e);
        }
    }

    private List<ChatMessage> getFromRedis(Long ticketId) {
        try {
            String key = REDIS_KEY_PREFIX + ticketId;
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

    private List<ChatMessage> getFromDb(Long ticketId) {
        try {
            List<TicketMessage> dbMessages = ticketMessageMapper.findByTicketId(ticketId);
            return dbMessages.stream()
                    .map(m -> new ChatMessage(
                            m.getSenderType(),
                            m.getContent(),
                            m.getIsAi(),
                            m.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("读取数据库历史失败, ticketId={}", ticketId, e);
            return new ArrayList<>();
        }
    }

    private void fillRedis(Long ticketId, List<ChatMessage> dbHistory) {
        try {
            // 只缓存最近的 MAX_REDIS_MESSAGES 条
            List<ChatMessage> toCache = dbHistory.size() > MAX_REDIS_MESSAGES
                    ? dbHistory.subList(dbHistory.size() - MAX_REDIS_MESSAGES, dbHistory.size())
                    : dbHistory;

            String key = REDIS_KEY_PREFIX + ticketId;
            String json = com.alibaba.fastjson2.JSON.toJSONString(toCache);
            redisTemplate.opsForValue().set(key, json, REDIS_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("回填Redis失败, ticketId={}", ticketId, e);
        }
    }

    /**
     * 对话消息记录
     */
    public record ChatMessage(String senderType, String content, boolean isAi, long timestamp) {}
}
