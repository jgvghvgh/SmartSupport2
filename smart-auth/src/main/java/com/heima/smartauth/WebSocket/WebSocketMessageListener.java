package com.heima.smartauth.WebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;
@Component
public class WebSocketMessageListener implements MessageListener {
    @Autowired
    private WebSocketUserRegistry registry;
    @Override
    public void onMessage(Message message, byte[] pattern) {
        Jackson2JsonRedisSerializer<WebSocketMessage> serializer = new Jackson2JsonRedisSerializer<>(WebSocketMessage.class);
        WebSocketMessage msg = serializer.deserialize(message.getBody());
        // 检查用户是否在本节点
        if (registry.hasUser(msg.getUserId())) {
            registry.sendMessage(msg.getUserId(), msg.getMessage());
        }
    }
}
