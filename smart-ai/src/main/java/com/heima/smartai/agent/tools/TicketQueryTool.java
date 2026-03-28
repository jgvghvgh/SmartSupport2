package com.heima.smartai.agent.tools;

import com.heima.smartai.agent.core.SimpleTool;
import com.heima.smartai.client.TicketRemoteClient;
import com.heima.smartai.model.TicketContent;
import com.heima.smartcommon.Result.CommonResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工单查询工具
 */
@Component
@RequiredArgsConstructor
public class TicketQueryTool implements SimpleTool {

    private final TicketRemoteClient ticketRemoteClient;

    @Override
    public String name() {
        return "ticket_query";
    }

    @Override
    public String description() {
        return "当需要查询工单详情、历史记录时使用。参数：ticket_id(必填，工单ID)";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String ticketId = (String) params.get("ticket_id");
        if (ticketId == null || ticketId.isBlank()) {
            return ToolResult.fail("ticket_id参数不能为空");
        }

        CommonResult<TicketContent> result = ticketRemoteClient.getTicketAttachment(Long.valueOf(ticketId));
        if (result == null || result.getData() == null) {
            return ToolResult.fail("工单不存在: " + ticketId);
        }

        TicketContent ticket = result.getData();
        StringBuilder sb = new StringBuilder();
        sb.append("【工单内容】\n").append(ticket.getContent()).append("\n\n");
        sb.append("【发送者ID】").append(ticket.getSenderId()).append("\n");
        if (ticket.getAttachments() != null && !ticket.getAttachments().isEmpty()) {
            sb.append("【附件数量】").append(ticket.getAttachments().size()).append("\n");
        }
        return ToolResult.ok(sb.toString());
    }
}
