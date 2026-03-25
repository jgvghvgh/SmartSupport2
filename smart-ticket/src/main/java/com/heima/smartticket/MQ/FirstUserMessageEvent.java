package com.heima.smartticket.MQ;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 用户首次发消息后触发：异步生成 AI 自动回复（after commit）。
 */
@Getter
public class FirstUserMessageEvent extends ApplicationEvent {
    private final Long ticketId;
    private final String userMessage;
    private final String imageUrl;
    private final String imageType;

    public FirstUserMessageEvent(Object source, Long ticketId, String userMessage) {
        this(source, ticketId, userMessage, null, null);
    }

    public FirstUserMessageEvent(Object source, Long ticketId, String userMessage, String imageUrl, String imageType) {
        super(source);
        this.ticketId = ticketId;
        this.userMessage = userMessage;
        this.imageUrl = imageUrl;
        this.imageType = imageType;
    }
}
