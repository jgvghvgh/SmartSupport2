package com.heima.smartauth.Mapper;

import com.heima.smartauth.Entity.Notification;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NotificationMapper {
    @Select("SELECT * FROM notification WHERE user_id = #{userId} AND read_flag = 0 ORDER BY created_at DESC")
    List<Notification> getUnreadByUserId(Long userId);

    @Update("UPDATE notification SET read_flag = 1 WHERE id = #{id}")
    void markAsRead(Long id);

    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND read_flag = 0")
    Long getUnreadCount(Long userId);
}