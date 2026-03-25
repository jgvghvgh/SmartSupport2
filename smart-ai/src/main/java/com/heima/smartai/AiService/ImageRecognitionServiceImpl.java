package com.heima.smartai.AiService;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.heima.smartai.Config.AIConfig;
import com.heima.smartticket.entity.TicketAttachment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * 图片识别服务实现类
 * 使用DashScope OCR服务进行文字识别
 * 需要配置 ai.access-key-id 和 ai.access-key-secret（API Key）
 * DashScope OCR API端点：https://dashscope.aliyuncs.com/api/v1/services/aocr/recognize
 */
@Slf4j
@Service
public class ImageRecognitionServiceImpl implements ImageRecognitionService {

    private final RestTemplate restTemplate;
    private final AIConfig aiConfig;

    // DashScope OCR API端点（通用文字识别）
    private static final String DASHSCOPE_OCR_API = "https://dashscope.aliyuncs.com/api/v1/services/aocr/recognize";
    // DashScope OCR 模型
    private static final String OCR_MODEL = "ocr";

    // 支持的图片格式
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/bmp", "image/gif"
    );

    @Autowired
    public ImageRecognitionServiceImpl(RestTemplate restTemplate, AIConfig aiConfig) {
        this.restTemplate = restTemplate;
        this.aiConfig = aiConfig;
    }

    @Override
    public String recognizeText(String imageUrl, String fileType) {
        if (!isSupportedImageType(fileType)) {
            log.warn("不支持的图片格式: {}", fileType);
            return null;
        }

        try {
            // 获取图片URL（DashScope OCR支持直接传URL）
            String imageUrlForOcr = getImageUrl(imageUrl);
            if (imageUrlForOcr == null) {
                log.error("无法获取图片URL: {}", imageUrl);
                return null;
            }

            // 调用DashScope OCR API
            return callDashScopeOcr(imageUrlForOcr);
        } catch (Exception e) {
            log.error("图片文字识别失败, imageUrl={}, error={}", imageUrl, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public String recognizeImageContent(String imageUrl, String fileType) {
        // 目前仅实现文字识别，后续可扩展图像理解功能
        return recognizeText(imageUrl, fileType);
    }

    @Override
    public String processTicketAttachments(List<TicketAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        result.append("\n【工单附件分析】\n");

        int imageCount = 0;
        for (TicketAttachment attachment : attachments) {
            if (isImageFile(attachment.getFileType())) {
                imageCount++;
                result.append(String.format("附件%d (%s): ", imageCount, attachment.getFileName()));

                String recognitionResult = recognizeText(attachment.getFileUrl(), attachment.getFileType());
                if (recognitionResult != null && !recognitionResult.trim().isEmpty()) {
                    result.append("识别到文字内容：").append(recognitionResult).append("\n");
                } else {
                    result.append("未识别到有效文字内容\n");
                }
            }
        }

        if (imageCount == 0) {
            return "";
        }

        return result.toString();
    }

    private boolean isSupportedImageType(String fileType) {
        if (fileType == null) {
            return false;
        }
        String lowerType = fileType.toLowerCase();
        return SUPPORTED_IMAGE_TYPES.contains(lowerType);
    }

    private String getImageUrl(String imageUrl) {
        try {
            // 直接返回URL，DashScope OCR支持通过URL识别
            return imageUrl;
        } catch (Exception e) {
            log.error("图片URL处理失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private String callDashScopeOcr(String imageUrl) {
        try {
            // DashScope OCR API请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("image_url", imageUrl);  // 支持直接传URL
            requestBody.put("model", OCR_MODEL);     // OCR模型
            // 可选参数
            requestBody.put("configure", Map.of(
                    "min_size", 16,
                    "output_prob", true
            ));

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + aiConfig.getAccessKeyId());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    DASHSCOPE_OCR_API,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                return parseDashScopeOcrResponse(response.getBody());
            } else {
                log.error("DashScope OCR API调用失败: {}", response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("调用DashScope OCR失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private String parseDashScopeOcrResponse(String responseBody) {
        try {
            JSONObject json = JSON.parseObject(responseBody);
            // DashScope OCR返回格式：{"output":{"text":"识别结果"}}
            if (json != null && json.containsKey("output")) {
                JSONObject output = json.getJSONObject("output");
                if (output.containsKey("text")) {
                    return output.getString("text");
                }
            }
            log.warn("DashScope OCR响应解析失败: {}", responseBody);
            return null;
        } catch (Exception e) {
            log.error("解析DashScope OCR响应失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取图片尺寸信息（辅助功能）
     */
    private Map<String, Integer> getImageDimensions(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            BufferedImage image = ImageIO.read(url);
            if (image != null) {
                Map<String, Integer> dimensions = new HashMap<>();
                dimensions.put("width", image.getWidth());
                dimensions.put("height", image.getHeight());
                return dimensions;
            }
        } catch (IOException e) {
            log.debug("无法获取图片尺寸: {}", e.getMessage());
        }
        return null;
    }
}