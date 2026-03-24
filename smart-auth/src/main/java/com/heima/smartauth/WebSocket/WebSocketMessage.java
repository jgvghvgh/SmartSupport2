package com.heima.smartauth.WebSocket;

public class WebSocketMessage {
    private Long userId;
    private String message;
    private String targetServerId;  // 目标服务器ID（用于分布式路由）

    public WebSocketMessage() {}

    public WebSocketMessage(Long userId, String message) {
        this.userId = userId;
        this.message = message;
    }

    public WebSocketMessage(Long userId, String message, String targetServerId) {
        this.userId = userId;
        this.message = message;
        this.targetServerId = targetServerId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTargetServerId() {
        return targetServerId;
    }

    public void setTargetServerId(String targetServerId) {
        this.targetServerId = targetServerId;
    }
}
