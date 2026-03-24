package com.heima.smartauth.WebSocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Service;

@Service
public class WebSocketMessagePublisher {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 广播消息（所有节点）
     */
    public void publishMessage(Long userId, String message) {
        WebSocketMessage msg = new WebSocketMessage(userId, message);
        Jackson2JsonRedisSerializer<WebSocketMessage> serializer = new Jackson2JsonRedisSerializer<>(WebSocketMessage.class);
        byte[] data = serializer.serialize(msg);
        redisTemplate.getConnectionFactory().getConnection().publish("websocket:messages".getBytes(), data);
    }

    /**
     * 定向消息（指定服务器节点）
     */
    public void publishMessage(Long userId, String message, String targetServerId) {
        WebSocketMessage msg = new WebSocketMessage(userId, message, targetServerId);
        Jackson2JsonRedisSerializer<WebSocketMessage> serializer = new Jackson2JsonRedisSerializer<>(WebSocketMessage.class);
        byte[] data = serializer.serialize(msg);
        // 发送到特定服务器的频道
        String channel = "ws:channel:" + targetServerId;
        redisTemplate.getConnectionFactory().getConnection().publish(channel.getBytes(), data);
    }
}
