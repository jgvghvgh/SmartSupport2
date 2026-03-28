package com.heima.smartticket.Service;

import com.heima.smartauth.WebSocket.WebSocketMessagePublisher;
import com.heima.smartticket.Mapper.NotificationMapper;
import com.heima.smartticket.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final WebSocketMessagePublisher publisher;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private NotificationMapper notificationMapper;

    public void notifyUser(Long userId, String message) {
        if (redisTemplate.hasKey("online:user:" + userId)) {
            publisher.publishMessage(userId, message);
        } else {
            saveOfflineNotification(userId, "USER_MESSAGE", message);
        }
    }

    public void notifyAgent(Long agentId, String message) {
        if (redisTemplate.hasKey("online:agent:" + agentId)) {
            publisher.publishMessage(agentId, message);
        } else {
            saveOfflineNotification(agentId, "AGENT_MESSAGE", message);
        }
    }

    private void saveOfflineNotification(Long userId, String type, String content) {
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setContent(content);
            notification.setReadFlag(false);
            notificationMapper.insert(notification);
            log.info("离线通知已保存: userId={}, type={}", userId, type);
        } catch (Exception e) {
            log.error("保存离线通知失败: userId={}, error={}", userId, e.getMessage());
        }
    }
}
