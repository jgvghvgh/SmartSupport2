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

    public FirstUserMessageEvent(Object source, Long ticketId, String userMessage) {
        super(source);
        this.ticketId = ticketId;
        this.userMessage = userMessage;
    }
}

