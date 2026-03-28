package com.heima.smartticket.Mapper;

import com.heima.smartticket.entity.Notification;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NotificationMapper {
    @Insert("insert into notification(user_id, type, content, read_flag) values(#{userId}, #{type}, #{content}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Notification notification);

    @Select("SELECT * FROM notification WHERE user_id = #{userId} AND read_flag = 0 ORDER BY created_at DESC")
    List<Notification> getUnreadByUserId(Long userId);

    @Update("UPDATE notification SET read_flag = 1 WHERE id = #{id}")
    void markAsRead(Long id);

    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND read_flag = 0")
    Long getUnreadCount(Long userId);
}