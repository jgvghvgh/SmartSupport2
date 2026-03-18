package com.heima.smartticket.Service;

import com.heima.smartauth.WebSocket.WebSocketUserRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final  WebSocketUserRegistry registry;

    public void notifyUser(Long userId, String message) {
        if (registry.isOnline(userId)) {
            registry.sendMessage(userId, message);
        } else {
            // 用户离线可扩展为：保存数据库 or 发邮件
            System.out.println("用户 " + userId + " 离线，暂存消息");
        }
    }
}
