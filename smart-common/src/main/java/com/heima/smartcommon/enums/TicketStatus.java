package com.heima.smartcommon.enums;

/**
 * 工单状态枚举
 */
public enum TicketStatus {
    NEW("新建"),
    ASSIGNED("已分配"),
    IN_PROGRESS("处理中"),
    WAITING_CUSTOMER("等待用户回复"),
    WAITING_AGENT("等待客服回复"),
    RESOLVED("已解决"),
    CLOSED("已关闭"),
    CANCELLED("已取消");

    private final String description;

    TicketStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 从字符串转换，不区分大小写
     */
    public static TicketStatus fromString(String status) {
        if (status == null) {
            return null;
        }
        try {
            return TicketStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 兼容旧的状态字符串
            for (TicketStatus ts : values()) {
                if (ts.name().equalsIgnoreCase(status)) {
                    return ts;
                }
            }
            return null;
        }
    }

    /**
     * 检查状态是否有效
     */
    public static boolean isValid(String status) {
        return fromString(status) != null;
    }
}