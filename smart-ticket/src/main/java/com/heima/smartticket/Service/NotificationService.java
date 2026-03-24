package com.heima.smartticket.Service;

import com.heima.smartauth.WebSocket.WebSocketMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final WebSocketMessagePublisher publisher;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void notifyUser(Long userId, String message) {

        // 判断用户是否在线,通过redis在线表判断
        if (redisTemplate.hasKey("online:user:" + userId)) {

            publisher.publishMessage(userId, message);
        }
         else {
            // 用户离线可扩展为：保存数据库 or 发邮件
            System.out.println("用户 " + userId + " 离线，暂存消息");
        }
    }

    public void notifyAgent(Long agentId, String message) {
        // 判断客服是否在线，通过redis在线表判断
        if (redisTemplate.hasKey("online:agent:" + agentId)) {
            publisher.publishMessage(agentId, message);
        } else {
            // 客服离线可扩展为：保存数据库或发送其他通知
            System.out.println("客服 " + agentId + " 离线，暂存消息");
        }
    }
}
