package com.heima.smartauth.WebSocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;

@Component
public class WebSocketMessageListener implements MessageListener {
    @Autowired
    private WebSocketUserRegistry registry;

    @Value("${ws.server.id:default}")
    private String serverId;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        Jackson2JsonRedisSerializer<WebSocketMessage> serializer = new Jackson2JsonRedisSerializer<>(WebSocketMessage.class);
        WebSocketMessage msg = serializer.deserialize(message.getBody());

        // 只处理发给本节点的消息
        if (msg.getTargetServerId() == null || msg.getTargetServerId().equals(serverId)) {
            // 检查用户是否在本节点
            if (registry.hasUser(msg.getUserId())) {
                try {
                    registry.sendMessage(msg.getUserId(), msg.getMessage());
                } catch (Exception e) {
                    // 如果发送失败，可能是session已关闭
                    log.error("发送消息失败: {}", e.getMessage());
                }
            }
        }
    }

    public String getServerId() {
        return serverId;
    }
}
