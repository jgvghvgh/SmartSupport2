package com.heima.smartauth.WebSocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class WebSocketUserRegistry {
    private final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private WebSocketMessagePublisher messagePublisher;

    @Value("${ws.server.id:default}")
    private String serverId;

    /**
     * 用户上线 - 保存session + 记录路由表
     */
    public void addUser(Long userId, WebSocketSession session) {
        sessions.put(userId, session);
        // 记录用户路由：userId -> serverId
        redisTemplate.opsForValue().set(
            "ws:route:" + userId,
            serverId,
            60,
            TimeUnit.SECONDS
        );
    }

    /**
     * 用户下线 - 清理session + 路由表
     */
    public void removeUser(Long userId) {
        sessions.remove(userId);
        redisTemplate.delete("ws:route:" + userId);
    }

    /**
     * 发送消息 - 支持分布式路由
     */
    public void sendMessage(Long userId, String message) {
        // 1. 先检查本地session
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 2. 本地没有，通过Redis路由表查找目标服务器
        String targetServer = (String) redisTemplate.opsForValue().get("ws:route:" + userId);
        if (targetServer != null && !targetServer.equals(serverId)) {
            // 转发到目标服务器
            messagePublisher.publishMessage(userId, message, targetServer);
        }
    }

    public boolean isOnline(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("online:user:" + userId));
    }

    public boolean hasUser(Long userId) {
        return sessions.containsKey(userId);
    }

    public String getServerId() {
        return serverId;
    }
}
