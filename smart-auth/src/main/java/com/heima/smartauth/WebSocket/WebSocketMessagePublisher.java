package com.heima.smartauth.WebSocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Service;

@Service
public class WebSocketMessagePublisher {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void publishMessage(Long userId, String message) {
        WebSocketMessage msg = new WebSocketMessage(userId, message);
        Jackson2JsonRedisSerializer<WebSocketMessage> serializer = new Jackson2JsonRedisSerializer<>(WebSocketMessage.class);
        byte[] data = serializer.serialize(msg);
        redisTemplate.getConnectionFactory().getConnection().publish("websocket:messages".getBytes(), data);
    }
}
