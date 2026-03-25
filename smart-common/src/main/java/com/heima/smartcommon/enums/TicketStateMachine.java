package com.heima.smartcommon.enums;

import java.util.*;

/**
 * 工单状态机
 * 定义状态流转规则
 */
public class TicketStateMachine {

    private static final Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = new HashMap<>();

    static {
        // NEW状态可以流转到：ASSIGNED（分配客服）、WAITING_AGENT（用户发送消息等待客服）、WAITING_CUSTOMER（AI回复等待用户）、CANCELLED（取消）
        TRANSITIONS.put(TicketStatus.NEW, EnumSet.of(
            TicketStatus.ASSIGNED,
            TicketStatus.WAITING_AGENT,
            TicketStatus.WAITING_CUSTOMER,
            TicketStatus.CANCELLED
        ));

        // ASSIGNED状态可以流转到：IN_PROGRESS（开始处理）、WAITING_AGENT（用户发送消息等待客服）、WAITING_CUSTOMER（AI回复等待用户）、CANCELLED（取消）
        TRANSITIONS.put(TicketStatus.ASSIGNED, EnumSet.of(
            TicketStatus.IN_PROGRESS,
            TicketStatus.WAITING_AGENT,
            TicketStatus.WAITING_CUSTOMER,
            TicketStatus.CANCELLED
        ));

        // IN_PROGRESS状态可以流转到：WAITING_CUSTOMER、WAITING_AGENT、RESOLVED、CLOSED
        TRANSITIONS.put(TicketStatus.IN_PROGRESS, EnumSet.of(
            TicketStatus.WAITING_CUSTOMER,
            TicketStatus.WAITING_AGENT,
            TicketStatus.RESOLVED,
            TicketStatus.CLOSED
        ));

        // WAITING_CUSTOMER状态可以流转到：IN_PROGRESS、RESOLVED、CLOSED、CANCELLED
        TRANSITIONS.put(TicketStatus.WAITING_CUSTOMER, EnumSet.of(
            TicketStatus.IN_PROGRESS,
            TicketStatus.RESOLVED,
            TicketStatus.CLOSED,
            TicketStatus.CANCELLED
        ));

        // WAITING_AGENT状态可以流转到：IN_PROGRESS、RESOLVED、CLOSED
        TRANSITIONS.put(TicketStatus.WAITING_AGENT, EnumSet.of(
            TicketStatus.IN_PROGRESS,
            TicketStatus.RESOLVED,
            TicketStatus.CLOSED
        ));

        // RESOLVED状态可以流转到：CLOSED（关闭工单）
        TRANSITIONS.put(TicketStatus.RESOLVED, EnumSet.of(
            TicketStatus.CLOSED
        ));

        // CLOSED和CANCELLED是终态，不能流转到其他状态
        TRANSITIONS.put(TicketStatus.CLOSED, Collections.emptySet());
        TRANSITIONS.put(TicketStatus.CANCELLED, Collections.emptySet());
    }

    /**
     * 检查状态流转是否允许
     */
    public static boolean canTransition(TicketStatus fromStatus, TicketStatus toStatus) {
        if (fromStatus == null || toStatus == null) {
            return false;
        }

        // 相同状态允许（无实际变化）
        if (fromStatus == toStatus) {
            return true;
        }

        Set<TicketStatus> allowedTransitions = TRANSITIONS.get(fromStatus);
        return allowedTransitions != null && allowedTransitions.contains(toStatus);
    }

    /**
     * 检查状态流转是否允许（字符串版本）
     */
    public static boolean canTransition(String fromStatus, String toStatus) {
        TicketStatus from = TicketStatus.fromString(fromStatus);
        TicketStatus to = TicketStatus.fromString(toStatus);
        return canTransition(from, to);
    }

    /**
     * 获取允许的下一个状态列表
     */
    public static Set<TicketStatus> getAllowedTransitions(TicketStatus currentStatus) {
        return TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
    }

    /**
     * 获取允许的下一个状态列表（字符串版本）
     */
    public static Set<String> getAllowedTransitions(String currentStatus) {
        TicketStatus status = TicketStatus.fromString(currentStatus);
        if (status == null) {
            return Collections.emptySet();
        }
        Set<TicketStatus> statusSet = getAllowedTransitions(status);
        Set<String> result = new HashSet<>();
        for (TicketStatus ts : statusSet) {
            result.add(ts.name());
        }
        return result;
    }

    /**
     * 检查状态是否为终态（不可再流转）
     */
    public static boolean isFinalStatus(TicketStatus status) {
        return status == TicketStatus.CLOSED || status == TicketStatus.CANCELLED;
    }

    /**
     * 检查状态是否为终态（字符串版本）
     */
    public static boolean isFinalStatus(String status) {
        TicketStatus ts = TicketStatus.fromString(status);
        return ts != null && isFinalStatus(ts);
    }
}