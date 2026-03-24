package com.heima.smartticket.Mapper;

import com.heima.smartticket.entity.OutboxMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OutboxMessageMapper {
    @Insert("insert into outbox_message(id, event_type, payload, created_time,status) values(#{id}, #{eventType}, #{payload}, #{createdTime},#{status} )")
    void insert(OutboxMessage outboxMessage);
    @Select("select * from outbox_message where status = 'PENDING'")
    List<OutboxMessage> selectPendingMessages();
    @Update("update outbox_message set status = 'SENT' where id = #{id}")
    void markAsSent(int id);
    @Update("update outbox_message set retry_count = retry_count + 1 where id = #{id}")
    void incrementRetry(int id);
    @Update("update outbox_message set status = #{failed} where id = #{id}")
    void updateStatus(int id, String failed);
    @Update("update outbox_message set status = 'FAILED' where id = #{id}")
    void markAsFailed(int id);
}
