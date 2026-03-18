package com.heima.smartticket.MQ;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeadLetterListener {

    @RabbitListener(queues = RabbitMqConfig.COMMENT_DEAD_QUEUE)
    public void handleDeadLetter(Message message) {
        log.error(" 死信消息: {}", new String(message.getBody()));
        // TODO: 可接入钉钉/飞书报警、人工处理、记录数据库
    }
}
