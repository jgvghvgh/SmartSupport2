package com.heima.smartai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 支持多模态的消息对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String role; // "user" / "assistant"
    private String content; // 文本内容
    private List<ImageContent> images; // 图片内容列表

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageContent {
        private String url;
        private String imageType;
    }

    /**
     * 构建纯文本消息
     */
    public static Message ofUser(String text) {
        return Message.builder()
                .role("user")
                .content(text)
                .build();
    }

    /**
     * 构建带图片的用户消息
     */
    public static Message ofUserWithImage(String text, String imageUrl, String imageType) {
        return Message.builder()
                .role("user")
                .content(text)
                .images(List.of(ImageContent.builder()
                        .url(imageUrl)
                        .imageType(imageType)
                        .build()))
                .build();
    }
}
