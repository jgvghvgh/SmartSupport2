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

        // 本地保存 session + 记录分布式路由表
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

        // 清理本地session + 路由表
        registry.removeUser(userId);

        String role = getrole(session);
        String key = "online:"+role +":"+ userId;
        redisTemplate.delete(key);

        // 如果是AGENT，从负载集合中移除
        if ("AGENT".equals(role)) {
            redisTemplate.opsForZSet().remove("agent:load", userId.toString());
        }

        System.out.println("用户 " + userId + " 下线");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();

        Long userId = null;
        String role = null;
        boolean isDistributedPing = false;

        // 支持两种心跳格式:
        // 1. 分布式心跳: "PING:{token}" - 可以从任何服务器处理，不依赖session
        // 2. 传统心跳: "PING" - 需要session中有token，只能在同一服务器处理
        if (payload.startsWith("PING:")) {
            // 分布式心跳: 从消息中提取token，格式: PING:{token}
            isDistributedPing = true;
            String token = payload.substring(5); // 去掉"PING:"前缀

            try {
                // 验证token并获取用户信息
                if (!jwtUtils.validateToken(token)) {
                    System.err.println("心跳检测: token无效");
                    return;
                }

                userId = JwtUtils.getUserId(token);
                role = jwtUtils.getRoleFromToken(token);

            } catch (Exception e) {
                System.err.println("心跳检测处理失败: " + e.getMessage());
                return;
            }
        } else if ("PING".equals(payload)) {
            // 传统心跳: 从session获取用户信息
            userId = getUserIdFromToken(session);
            role = getrole(session);
        } else {
            // 其他消息类型
            return;
        }

        if (userId == null || role == null) {
            System.err.println("心跳检测: 无法获取用户信息");
            return;
        }

        // 先查找看redis用户表是否存在
        String key = "online:" + role + ":" + userId;
        if (redisTemplate.opsForValue().get(key) == null) {
            // 不存在重新建立在线
            String connectionId = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    key,
                    connectionId,
                    60,
                    TimeUnit.SECONDS
            );
            if (role.equals("AGENT")) {
                redisTemplate.opsForZSet()
                        .add("agent:load", userId.toString(), 0);
            }
        }
        // 存在则刷新redis的过期时间
        redisTemplate.expire(
                key,
                60,
                TimeUnit.SECONDS
        );
        // 同时刷新路由表TTL
        redisTemplate.expire(
                "ws:route:" + userId,
                60,
                TimeUnit.SECONDS
        );

        System.out.println("用户 " + userId + " 心跳刷新成功" + (isDistributedPing ? " (分布式)" : ""));
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
