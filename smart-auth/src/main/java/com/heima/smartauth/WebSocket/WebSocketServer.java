package com.heima.smartauth.WebSocket;

import com.heima.smartauth.Utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class WebSocketServer extends TextWebSocketHandler {

    @Autowired
    private WebSocketUserRegistry registry;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {

        Long userId = getUserIdFromToken(session);


        // 本地保存 session
        registry.addUser(userId, session);

        // 生成连接ID
        String connectionId = UUID.randomUUID().toString();

        // Redis key

        String role= getrole(session);
        String key = "online:"+role +":"+ userId;
        if(role.equals("AGENT")){
            redisTemplate.opsForZSet()
                    .add("agent:load", userId.toString(), 0);
        }
        // 写入 Redis (TTL 60秒)
        redisTemplate.opsForValue().set(
                key,
                connectionId,
                60,
                TimeUnit.SECONDS
        );

        System.out.println("用户 " + userId + " 已上线");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserIdFromToken(session);

        registry.removeUser(userId);

        String role = getrole(session);
        String key = "online:"+role +":"+ userId;
        redisTemplate.delete(key);

        System.out.println("用户 " + userId + " 下线");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        String role= getrole(session);
        if ("PING".equals(payload)) {
            Long userId = getUserIdFromToken(session);
            //先查找看redis用户表是否存在
            String key = "online:"+role +":"+ userId;
            if(redisTemplate.opsForValue().get(key) == null){
                //不存在重新建立在线
                String connectionId = UUID.randomUUID().toString();
                redisTemplate.opsForValue().set(
                        key,
                        connectionId,
                        60,
                        TimeUnit.SECONDS
                );
                if(role.equals("AGENT")){
                    redisTemplate.opsForZSet()
                            .add("agent:load", userId.toString(), 0);
                }
            }
            //存在则刷新redis的过期时间
            redisTemplate.expire(
                    key,
                    60,
                    TimeUnit.SECONDS
            );
        }
    }

    private Long getUserIdFromToken(WebSocketSession session) {
        // 从 URL 或 Header 中提取 token -> userId
        String token = (String) session.getAttributes().get("token");
        return JwtUtils.getUserId(token);
    }
    public String getrole(WebSocketSession session){
        String token = (String) session.getAttributes().get("token");
        String role= jwtUtils.getRoleFromToken(token);
        return role;
    }
}
