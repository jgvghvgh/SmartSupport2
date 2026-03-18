package com.heima.smartticket.MQ;

import com.heima.smartticket.Mapper.OutboxMessageMapper;
import com.heima.smartticket.entity.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxMessageMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;

    // 每5秒扫描未发送的消息
    @Scheduled(fixedDelay = 5000)
    public void publishPendingMessages() {
        List<OutboxMessage> pending = outboxMapper.selectPendingMessages();
        for (OutboxMessage msg : pending) {
            try {
                rabbitTemplate.convertAndSend("ticket.exchange", msg.getEventType(), msg.getPayload());
                outboxMapper.markAsSent(msg.getId());
            } catch (Exception e) {
                outboxMapper.incrementRetry(msg.getId());
                System.out.println("发送工单消息失败, id=" + msg.getId() + ", error=" + e.getMessage());
            }
        }
    }
}
