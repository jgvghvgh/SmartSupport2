package com.heima.smartauth.WebSocket;

import com.heima.smartauth.Utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketServer extends TextWebSocketHandler {

    private final WebSocketUserRegistry registry;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtils jwtUtils;

    private static final String ONLINE_KEY_PREFIX = "online:";
    private static final long HEARTBEAT_TTL_SECONDS = 60L;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserIdFromSession(session);
        String role = getRoleFromSession(session);

        // 本地保存 session + 记录分布式路由表
        registry.addUser(userId, session);

        // 设置在线状态
        String onlineKey = ONLINE_KEY_PREFIX + role.toLowerCase() + ":" + userId;
        redisTemplate.opsForValue().set(onlineKey, "1", HEARTBEAT_TTL_SECONDS, TimeUnit.SECONDS);

        // 客服：只有首次连接才初始化负载（ZSet中不存在才添加）
        // 重连时保持原有负载不变，避免网络波动导致负载丢失
        if ("AGENT".equalsIgnoreCase(role)) {
            Double existingLoad = redisTemplate.opsForZSet().score("agent:load", userId.toString());
            if (existingLoad == null) {
                redisTemplate.opsForZSet().add("agent:load", userId.toString(), 0);
            }
        }

        log.info("用户 {} 已上线", userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserIdFromSession(session);
        String role = getRoleFromSession(session);

        // 清理本地 session + 路由表
        registry.removeUser(userId);

        // 不删除 online:agent:{id}，依靠 Redis TTL 自然过期
        // 60秒内重连则负载保持，不影响工单处理
        // 60秒后过期由 RedisKeyExpirationListener 清理 agent:load

        log.info("用户 {} WebSocket连接断开（60s内重连则负载保留）", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();

        Long userId;
        String role;

        if (payload.startsWith("PING:")) {
            // 分布式心跳: PING:{token}
            String token = payload.substring(5);
            if (!jwtUtils.validateToken(token)) {
                log.warn("心跳检测: token无效");
                return;
            }
            try {
                userId = JwtUtils.getUserId(token);
                role = jwtUtils.getRoleFromToken(token);
            } catch (Exception e) {
                log.error("心跳检测解析失败: {}", e.getMessage());
                return;
            }
        } else if ("PING".equals(payload)) {
            // 传统心跳: PING
            userId = getUserIdFromSession(session);
            role = getRoleFromSession(session);
        } else {
            return;
        }

        if (userId == null || role == null) {
            log.warn("心跳检测: 无法获取用户信息");
            return;
        }

        // 刷新在线状态 TTL
        String onlineKey = ONLINE_KEY_PREFIX + role.toLowerCase() + ":" + userId;
        Boolean exists = redisTemplate.hasKey(onlineKey);
        if (Boolean.FALSE.equals(exists)) {
            // key 不存在（可能是超时下线后重新发心跳），重建 online 状态
            // agent:load 如已过期被清理则不再重建，由工单分配时重新初始化
            redisTemplate.opsForValue().set(onlineKey, "1", HEARTBEAT_TTL_SECONDS, TimeUnit.SECONDS);
            // 重建路由表
            registry.refreshRoute(userId);
        } else {
            redisTemplate.expire(onlineKey, HEARTBEAT_TTL_SECONDS, TimeUnit.SECONDS);
            // 刷新路由表 TTL
            registry.refreshRoute(userId);
        }

        log.debug("心跳刷新成功: userId={}", userId);
    }

    private Long getUserIdFromSession(WebSocketSession session) {
        String token = (String) session.getAttributes().get("token");
        return JwtUtils.getUserId(token);
    }

    private String getRoleFromSession(WebSocketSession session) {
        String token = (String) session.getAttributes().get("token");
        return jwtUtils.getRoleFromToken(token);
    }
}
