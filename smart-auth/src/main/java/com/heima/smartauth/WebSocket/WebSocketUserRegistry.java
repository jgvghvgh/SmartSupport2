package com.heima.smartauth.WebSocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketUserRegistry {
    private final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    public void addUser(Long userId, WebSocketSession session) {
        sessions.put(userId, session);
    }

    public void removeUser(Long userId) {
        sessions.remove(userId);
    }

    public void sendMessage(Long userId, String message) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isOnline(Long userId) {
        return redisTemplate.hasKey("online:user:" + userId);
    }

    public boolean hasUser(Long userId) {
        return sessions.containsKey(userId);
    }
}
