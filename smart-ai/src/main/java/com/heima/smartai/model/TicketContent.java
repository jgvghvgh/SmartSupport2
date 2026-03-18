package com.heima.smartai.model;

import com.heima.smartticket.entity.TicketAttachment;
import jakarta.annotation.sql.DataSourceDefinition;
import org.apache.ibatis.annotations.Delete;

import java.util.List;


public class TicketContent {
    private List<TicketAttachment> attachments;
    private String content;
    private String createdAt;
    private String senderId;

    public List<TicketAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<TicketAttachment> attachments) {
        this.attachments = attachments;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
}
