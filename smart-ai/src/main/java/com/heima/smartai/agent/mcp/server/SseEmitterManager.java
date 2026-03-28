package com.heima.smartai.agent.mcp.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE连接管理器 - 管理客户端SSE连接
 */
@Slf4j
@Component
public class SseEmitterManager {
    // clientId -> SseEmitter
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    // 客户端标识 -> clientId
    private final Map<String, String> clientIds = new ConcurrentHashMap<>();

    /**
     * 创建新的SSE连接
     */
    public SseEmitter createEmitter(String clientId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // 无超时

        emitter.onCompletion(() -> {
            log.debug("SSE连接完成, clientId={}", clientId);
            emitters.remove(clientId);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE连接超时, clientId={}", clientId);
            emitters.remove(clientId);
        });

        emitter.onError(e -> {
            log.warn("SSE连接错误, clientId={}, error={}", clientId, e.getMessage());
            emitters.remove(clientId);
        });

        emitters.put(clientId, emitter);
        log.info("新的SSE连接, clientId={}", clientId);
        return emitter;
    }

    /**
     * 发送事件给指定客户端
     */
    public void sendEvent(String clientId, String event, String data) {
        SseEmitter emitter = emitters.get(clientId);
        if (emitter == null) {
            log.warn("SSE客户端不存在, clientId={}", clientId);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(data));
            log.debug("SSE事件发送成功, clientId={}, event={}", clientId, event);
        } catch (IOException e) {
            log.warn("SSE事件发送失败, clientId={}, error={}", clientId, e.getMessage());
            emitters.remove(clientId);
        }
    }

    /**
     * 广播事件给所有客户端
     */
    public void broadcastEvent(String event, String data) {
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event()
                        .name(event)
                        .data(data));
            } catch (IOException e) {
                log.warn("SSE广播失败, clientId={}", entry.getKey());
            }
        }
    }

    /**
     * 关闭指定客户端连接
     */
    public void close(String clientId) {
        SseEmitter emitter = emitters.remove(clientId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public int getConnectionCount() {
        return emitters.size();
    }
}
