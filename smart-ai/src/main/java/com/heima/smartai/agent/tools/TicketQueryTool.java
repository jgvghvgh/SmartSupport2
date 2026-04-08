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
 * 用于查询指定工单的详细内容、附件信息等
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
        return """
            查询指定工单的详细内容、附件和历史记录。

            【使用场景】
            - 用户询问自己提交的工单处理进度
            - 需要查看工单的详细内容来理解问题
            - 需要获取工单附件信息
            - 用户提供了工单ID，需要查看该工单的具体情况

            【返回内容】
            - 工单主体内容（content）
            - 发送者ID
            - 附件数量和列表

            【使用建议】
            - 调用时需要提供有效的工单ID
            - 工单ID通常由用户提供，或从上下文中获取
            - 如果工单不存在或无权限访问，会返回错误信息
            """;
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "ticket_id", Map.of(
                                "type", "string",
                                "description", "工单ID，用于唯一标识一个工单。用户询问工单进度或详情时必须提供。"
                        )
                ),
                "required", java.util.List.of("ticket_id")
        );
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
