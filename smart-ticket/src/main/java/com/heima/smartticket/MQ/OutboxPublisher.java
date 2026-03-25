package com.heima.smartticket.MQ;

import com.heima.smartticket.Mapper.OutboxMessageMapper;
import com.heima.smartticket.entity.OutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxMessageMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;

    // 每5秒扫描未发送的消息
    @Scheduled(fixedDelay = 50000)
    public void publishPendingMessages() {
        List<OutboxMessage> pending = outboxMapper.selectPendingMessages();
        for (OutboxMessage msg : pending) {
            try {
                //如果重试次数达到3次，说明之前已经尝试过发送了，进入死信队列
                if (msg.getRetryCount() >= 3) {
                    rabbitTemplate.convertAndSend("dlx.exchange", "dlq.routingKey", msg.getPayload());
                    outboxMapper.updateStatus(msg.getId(), "FAILED");
                    outboxMapper.markAsFailed(msg.getId());
                    log.error("发送工单消息失败, id={}, error={}", msg.getId(), "重试次数过多");
                }
                rabbitTemplate.convertAndSend("ticket.exchange", msg.getEventType(), msg.getPayload());
                outboxMapper.markAsSent(msg.getId());
            } catch (Exception e) {
                outboxMapper.incrementRetry(msg.getId());
                log.error("发送工单消息失败, id={}, error={}", msg.getId(), e.getMessage());
            }
        }
    }
}
