package com.heima.smartcommon.enums;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TicketStateMachine状态机测试
 */
class TicketStateMachineTest {

    @Test
    void testCanTransitionValid() {
        // 有效的状态流转
        assertTrue(TicketStateMachine.canTransition(TicketStatus.NEW, TicketStatus.ASSIGNED));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.NEW, TicketStatus.CANCELLED));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.ASSIGNED, TicketStatus.CANCELLED));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_CUSTOMER));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_AGENT));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.WAITING_CUSTOMER, TicketStatus.IN_PROGRESS));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.WAITING_CUSTOMER, TicketStatus.CLOSED));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.WAITING_AGENT, TicketStatus.IN_PROGRESS));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.WAITING_AGENT, TicketStatus.RESOLVED));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.WAITING_AGENT, TicketStatus.CLOSED));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.RESOLVED, TicketStatus.CLOSED));

        // 相同状态允许
        assertTrue(TicketStateMachine.canTransition(TicketStatus.NEW, TicketStatus.NEW));
        assertTrue(TicketStateMachine.canTransition(TicketStatus.CLOSED, TicketStatus.CLOSED));
    }

    @Test
    void testCanTransitionInvalid() {
        // 无效的状态流转
        assertFalse(TicketStateMachine.canTransition(TicketStatus.NEW, TicketStatus.IN_PROGRESS));
        assertFalse(TicketStateMachine.canTransition(TicketStatus.NEW, TicketStatus.RESOLVED));
        assertFalse(TicketStateMachine.canTransition(TicketStatus.ASSIGNED, TicketStatus.NEW));
        assertFalse(TicketStateMachine.canTransition(TicketStatus.IN_PROGRESS, TicketStatus.ASSIGNED));
        assertFalse(TicketStateMachine.canTransition(TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS));
        assertFalse(TicketStateMachine.canTransition(TicketStatus.CLOSED, TicketStatus.RESOLVED));
        assertFalse(TicketStateMachine.canTransition(TicketStatus.CLOSED, TicketStatus.NEW));
        assertFalse(TicketStateMachine.canTransition(TicketStatus.CANCELLED, TicketStatus.NEW));
    }

    @Test
    void testCanTransitionStringVersion() {
        // 字符串版本测试
        assertTrue(TicketStateMachine.canTransition("NEW", "ASSIGNED"));
        assertTrue(TicketStateMachine.canTransition("new", "assigned")); // 不区分大小写
        assertFalse(TicketStateMachine.canTransition("NEW", "IN_PROGRESS"));
        assertFalse(TicketStateMachine.canTransition("INVALID", "NEW"));
        assertFalse(TicketStateMachine.canTransition("NEW", "INVALID"));
        assertFalse(TicketStateMachine.canTransition(null, "NEW"));
        assertFalse(TicketStateMachine.canTransition("NEW", null));
    }

    @Test
    void testGetAllowedTransitions() {
        // 测试获取允许的下一个状态
        Set<TicketStatus> newTransitions = TicketStateMachine.getAllowedTransitions(TicketStatus.NEW);
        assertEquals(2, newTransitions.size());
        assertTrue(newTransitions.contains(TicketStatus.ASSIGNED));
        assertTrue(newTransitions.contains(TicketStatus.CANCELLED));

        Set<TicketStatus> inProgressTransitions = TicketStateMachine.getAllowedTransitions(TicketStatus.IN_PROGRESS);
        assertEquals(3, inProgressTransitions.size());
        assertTrue(inProgressTransitions.contains(TicketStatus.WAITING_CUSTOMER));
        assertTrue(inProgressTransitions.contains(TicketStatus.WAITING_AGENT));
        assertTrue(inProgressTransitions.contains(TicketStatus.RESOLVED));

        Set<TicketStatus> closedTransitions = TicketStateMachine.getAllowedTransitions(TicketStatus.CLOSED);
        assertTrue(closedTransitions.isEmpty());

        Set<TicketStatus> cancelledTransitions = TicketStateMachine.getAllowedTransitions(TicketStatus.CANCELLED);
        assertTrue(cancelledTransitions.isEmpty());
    }

    @Test
    void testIsFinalStatus() {
        // 测试终态判断
        assertTrue(TicketStateMachine.isFinalStatus(TicketStatus.CLOSED));
        assertTrue(TicketStateMachine.isFinalStatus(TicketStatus.CANCELLED));
        assertFalse(TicketStateMachine.isFinalStatus(TicketStatus.NEW));
        assertFalse(TicketStateMachine.isFinalStatus(TicketStatus.ASSIGNED));
        assertFalse(TicketStateMachine.isFinalStatus(TicketStatus.IN_PROGRESS));
        assertFalse(TicketStateMachine.isFinalStatus(TicketStatus.RESOLVED));

        // 字符串版本
        assertTrue(TicketStateMachine.isFinalStatus("CLOSED"));
        assertTrue(TicketStateMachine.isFinalStatus("closed"));
        assertFalse(TicketStateMachine.isFinalStatus("NEW"));
        assertFalse(TicketStateMachine.isFinalStatus("invalid"));
        assertFalse(TicketStateMachine.isFinalStatus(null));
    }
}