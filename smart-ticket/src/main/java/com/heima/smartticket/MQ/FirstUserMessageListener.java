package com.heima.smartticket.MQ;

import com.heima.smartticket.Mapper.TicketMessageMapper;
import com.heima.smartticket.Mapper.TicketMapper;
import com.heima.smartticket.Service.NotificationService;
import com.heima.smartticket.client.AiAnalysisResultResponse;
import com.heima.smartticket.client.AiRemoteClient;
import com.heima.smartticket.entity.Ticket;
import com.heima.smartticket.entity.TicketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirstUserMessageListener {

    private final AiRemoteClient aiRemoteClient;
    private final TicketMapper ticketMapper;
    private final TicketMessageMapper ticketMessageMapper;
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onFirstUserMessage(FirstUserMessageEvent event) {
        Long ticketId = event.getTicketId();
        String userMessage = event.getUserMessage();

        // 幂等：避免重复事件导致重复写 AI 消息
        if (Boolean.TRUE.equals(ticketMessageMapper.existsAiMessage(ticketId))) {
            return;
        }

        Ticket ticket = ticketMapper.findById(ticketId);
        if (ticket == null) {
            log.warn("ticket not found: {}", ticketId);
            return;
        }

        try {
            AiAnalysisResultResponse resp =
                    aiRemoteClient.chat(userMessage, String.valueOf(ticketId));

            String aiReply = (resp == null || resp.getReferenceReply() == null)
                    ? null
                    : resp.getReferenceReply().trim();

            if (aiReply == null || aiReply.isEmpty()) {
                aiReply = "请客服补充/请重新描述";
            }

            // 入库 AI 消息（不走 outbox，避免再次 MQ 通知）
            TicketMessage aiMessage = new TicketMessage();
            aiMessage.setTicketId(ticketId);
            aiMessage.setSenderId(0L);
            aiMessage.setSenderType("AI");
            aiMessage.setContent(aiReply);
            aiMessage.setIsAi((short) 1);
            ticketMessageMapper.insert(aiMessage);

            // 推送给用户
            notificationService.notifyUser(ticket.getUserId(), aiReply);
        } catch (Exception e) {
            log.error("AI 自动回复失败, ticketId={}, error={}", ticketId, e.getMessage(), e);
        }
    }
}

