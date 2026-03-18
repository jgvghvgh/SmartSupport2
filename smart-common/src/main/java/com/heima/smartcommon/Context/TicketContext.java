package com.heima.smartcommon.Context;

import lombok.Data;

@Data
public class TicketContext {
    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    public static void setTicketId(Long id) {
        threadLocal.set(id);
    }

    public static Long getTicketId() {
        return threadLocal.get();
    }

    public static void removeTicketId() {
        threadLocal.remove();
    }
}
