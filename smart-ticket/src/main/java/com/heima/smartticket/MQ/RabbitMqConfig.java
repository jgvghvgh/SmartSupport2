package com.heima.smartticket.MQ;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

    /** 正常交换机 */
    public static final String EXCHANGE = "ticket.exchange";
    /** 评论队列 */
    public static final String COMMENT_QUEUE = "ticket.comment.queue";
    /** 死信交换机 */
    public static final String DEAD_EXCHANGE = "ticket.dead.exchange";
    /** 死信队列 */
    public static final String COMMENT_DEAD_QUEUE = "ticket.comment.dead.queue";

    /** 路由键 */
    public static final String COMMENT_ROUTING_KEY = "ticket.comment";
    public static final String COMMENT_DEAD_ROUTING_KEY = "ticket.comment.dead";

    /**
     * 正常 TopicExchange
     */
    @Bean
    public TopicExchange ticketExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /**
     * 死信交换机
     */
    @Bean
    public TopicExchange deadExchange() {
        return new TopicExchange(DEAD_EXCHANGE, true, false);
    }

    /**
     * 评论队列：绑定死信交换机
     */
    @Bean
    public Queue commentQueue() {
       Map<String, Object> args = new HashMap<>();
        // 配置死信交换机和路由键
        args.put("x-dead-letter-exchange", DEAD_EXCHANGE);
        args.put("x-dead-letter-routing-key", COMMENT_DEAD_ROUTING_KEY);
        // 可选：消息过期时间（例如 1 分钟），测试时可省略
        // args.put("x-message-ttl", 60000);
        return QueueBuilder.durable(COMMENT_QUEUE)
                .withArguments(args)
                .build();
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue commentDeadQueue() {
        return QueueBuilder.durable(COMMENT_DEAD_QUEUE).build();
    }

    /**
     * 绑定：正常队列
     */
    @Bean
    public Binding commentBinding() {
        return BindingBuilder.bind(commentQueue())
                .to(ticketExchange())
                .with(COMMENT_ROUTING_KEY);
    }

    /**
     * 绑定：死信队列
     */
    @Bean
    public Binding deadBinding() {
        return BindingBuilder.bind(commentDeadQueue())
                .to(deadExchange())
                .with(COMMENT_DEAD_ROUTING_KEY);
    }
}

