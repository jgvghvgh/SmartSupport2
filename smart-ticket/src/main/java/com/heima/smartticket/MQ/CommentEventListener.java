package com.heima.smartticket.MQ;

import com.heima.smartticket.Mapper.OutboxMessageMapper;
import com.heima.smartticket.entity.OutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommentEventListener {

    private final RabbitTemplate rabbitTemplate;
    @Autowired
    private OutboxMessageMapper outboxMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCommentCreated(CommentCreatedEvent event) {
        OutboxMessage msg = event.getMessage();
        try {
            //重试次数达到3次，说明之前已经尝试过发送了，进入死信队列
            if (msg.getRetryCount() >= 3) {
                rabbitTemplate.convertAndSend("dlx.exchange", "dlq.routingKey", msg.getPayload());
                outboxMapper.updateStatus(msg.getId(), "FAILED");
                outboxMapper.markAsFailed(msg.getId());
                log.error("评论消息发送失败超过3次, id={}, 进入死信队列", msg.getId());
            }
            // 发送消息到 MQ
            rabbitTemplate.convertAndSend("comment.exchange", "comment.created", msg.getPayload());
            outboxMapper.markAsSent(msg.getId());
        } catch (Exception e) {
            // MQ 异常时：标记失败 + 记录日志
            outboxMapper.incrementRetry(msg.getId());
            outboxMapper.updateStatus(msg.getId(), "PENDING");
            log.error("发送评论消息失败, id={}, error={}", msg.getId(), e.getMessage());
        }
    }

}
