package com.heima.smartai.AiService;

import com.heima.smartticket.entity.TicketAttachment;

import java.util.List;

/**
 * 图片识别服务接口
 * 支持OCR文字识别和图像内容理解
 */
public interface ImageRecognitionService {

    /**
     * 识别单张图片中的文字内容
     * @param imageUrl 图片URL
     * @param fileType 文件类型（如 image/png）
     * @return 识别出的文字内容，如果识别失败返回null
     */
    String recognizeText(String imageUrl, String fileType);

    /**
     * 识别图片内容（包括文字和图像理解）
     * @param imageUrl 图片URL
     * @param fileType 文件类型
     * @return 图片内容描述，包括文字和图像特征
     */
    String recognizeImageContent(String imageUrl, String fileType);

    /**
     * 批量处理工单附件中的图片
     * @param attachments 附件列表
     * @return 图片识别结果摘要，格式化为文本
     */
    String processTicketAttachments(List<TicketAttachment> attachments);

    /**
     * 检查文件类型是否为图片
     * @param fileType 文件类型
     * @return 是否为图片
     */
    default boolean isImageFile(String fileType) {
        if (fileType == null) {
            return false;
        }
        String lowerType = fileType.toLowerCase();
        return lowerType.startsWith("image/");
    }
}