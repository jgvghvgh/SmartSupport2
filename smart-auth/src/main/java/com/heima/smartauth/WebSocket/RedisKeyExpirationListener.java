package com.heima.smartauth.WebSocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
public class RedisKeyExpirationListener
        extends KeyExpirationEventMessageListener {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public RedisKeyExpirationListener(
            RedisMessageListenerContainer listenerContainer) {
        super(listenerContainer);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = message.toString();

        if(key.startsWith("online:agent:")){

            Long csId =
                Long.valueOf(key.split(":")[2]);
            // 从负载表删除
            redisTemplate.opsForZSet()
                    .remove("agent:load", csId.toString());

            System.out.println("客服下线："+csId);
        }
    }
}