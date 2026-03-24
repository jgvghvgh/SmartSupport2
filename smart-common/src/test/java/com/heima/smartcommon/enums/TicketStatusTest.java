package com.heima.smartcommon.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TicketStatus枚举测试
 */
class TicketStatusTest {

    @Test
    void testFromString() {
        // 测试大写转换
        assertEquals(TicketStatus.NEW, TicketStatus.fromString("NEW"));
        assertEquals(TicketStatus.ASSIGNED, TicketStatus.fromString("ASSIGNED"));
        assertEquals(TicketStatus.IN_PROGRESS, TicketStatus.fromString("IN_PROGRESS"));
        assertEquals(TicketStatus.WAITING_CUSTOMER, TicketStatus.fromString("WAITING_CUSTOMER"));
        assertEquals(TicketStatus.WAITING_AGENT, TicketStatus.fromString("WAITING_AGENT"));
        assertEquals(TicketStatus.RESOLVED, TicketStatus.fromString("RESOLVED"));
        assertEquals(TicketStatus.CLOSED, TicketStatus.fromString("CLOSED"));
        assertEquals(TicketStatus.CANCELLED, TicketStatus.fromString("CANCELLED"));

        // 测试小写转换
        assertEquals(TicketStatus.NEW, TicketStatus.fromString("new"));
        assertEquals(TicketStatus.ASSIGNED, TicketStatus.fromString("assigned"));
        assertEquals(TicketStatus.CLOSED, TicketStatus.fromString("closed"));

        // 测试无效状态
        assertNull(TicketStatus.fromString("INVALID_STATUS"));
        assertNull(TicketStatus.fromString(""));
        assertNull(TicketStatus.fromString(null));
    }

    @Test
    void testIsValid() {
        assertTrue(TicketStatus.isValid("NEW"));
        assertTrue(TicketStatus.isValid("new"));
        assertTrue(TicketStatus.isValid("CLOSED"));
        assertFalse(TicketStatus.isValid("INVALID"));
        assertFalse(TicketStatus.isValid(""));
        assertFalse(TicketStatus.isValid(null));
    }

    @Test
    void testGetDescription() {
        assertEquals("新建", TicketStatus.NEW.getDescription());
        assertEquals("已分配", TicketStatus.ASSIGNED.getDescription());
        assertEquals("已关闭", TicketStatus.CLOSED.getDescription());
        assertEquals("已取消", TicketStatus.CANCELLED.getDescription());
    }
}