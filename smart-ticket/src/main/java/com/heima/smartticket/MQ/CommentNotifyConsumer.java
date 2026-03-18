package com.heima.smartticket.MQ;



import com.alibaba.fastjson2.JSON;
import com.heima.smartticket.Mapper.TicketMapper;
import com.heima.smartticket.Service.NotificationService;
import com.heima.smartticket.Service.TicketService;
import com.heima.smartticket.entity.TicketMessage;
import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentNotifyConsumer {
    @Autowired
    private TicketService ticketService;
    @Autowired
    private TicketMapper ticketMapper;
    @Autowired
    private NotificationService notificationService;

    private static final int MAX_RETRY_COUNT = 3;

    @RabbitListener(queues = com.heima.smartticket.MQ.RabbitMqConfig.COMMENT_QUEUE)
    public void handleComment(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String body = new String(message.getBody());

        try {
            // 反序列化
            TicketMessage comment = JSON.parseObject(body, TicketMessage.class);

            //  校验消息是否合法（防止假消息）
            if (comment == null || comment.getId() == null || !ticketService.Messageexists(comment.getId())) {
                log.warn(" 无效评论消息: {}", body);
                channel.basicAck(tag, false);
                return;
            }

            // 获取被评论用户
            Long toUserId = ticketMapper.findById(comment.getTicketId()).getUserId();

            // 执行异步通知逻辑（邮件 / WebSocket / 站内信）
            notificationService.notifyUser(toUserId, "你的工单被评论：" + comment.getContent());

            // 手动确认消息成功
            channel.basicAck(tag, false);
            log.info(" 评论通知发送成功: {}", comment);

        } catch (Exception e) {
            // 获取当前重试次数
            Integer retryCount = (Integer) message.getMessageProperties()
                    .getHeaders()
                    .getOrDefault("x-retry-count", 0);

            if (retryCount < MAX_RETRY_COUNT) {
                // 增加重试次数头
                message.getMessageProperties().getHeaders().put("x-retry-count", retryCount + 1);
                log.warn(" 评论通知失败，第 {} 次重试: {}", retryCount + 1, e.getMessage());
                // 重新入队等待重试
                channel.basicNack(tag, false, true);
            } else {
                log.error(" 评论通知失败，超过最大重试次数，转入死信队列: {}", body);
                // 拒绝消息，进入死信队列
                channel.basicNack(tag, false, false);
            }
        }
    }
}