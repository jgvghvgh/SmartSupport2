package com.heima.smartticket.MQ;

import com.heima.smartticket.entity.OutboxMessage;
import org.springframework.context.ApplicationEvent;
import lombok.Getter;

/**
 * 评论创建事件 - 用于在事务提交后触发消息发送
 */
@Getter
public class CommentCreatedEvent extends ApplicationEvent {

    private final OutboxMessage message;

    public CommentCreatedEvent(Object source, OutboxMessage message) {
        super(source);
        this.message = message;
    }
}
