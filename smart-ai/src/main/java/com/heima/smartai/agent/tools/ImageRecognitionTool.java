package com.heima.smartai.agent.tools;

import com.heima.smartai.agent.core.SimpleTool;
import com.heima.smartai.AiService.ImageRecognitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 图片识别工具
 * 识别图片中的文字内容和视觉信息
 */
@Component
@RequiredArgsConstructor
public class ImageRecognitionTool implements SimpleTool {

    private final ImageRecognitionService imageRecognitionService;

    @Override
    public String name() {
        return "image_recognition";
    }

    @Override
    public String description() {
        return """
            识别图片中的文字内容或理解图片信息。

            【使用场景】
            - 用户上传了一张截图、照片或图片
            - 图片中包含需要识别的重要信息（错误信息、界面截图、配置截图等）
            - 用户发送了二维码、条形码需要扫描
            - 图片中展示了产品界面、操作步骤等需要 AI 理解的内容

            【支持格式】
            - 支持：PNG、JPG、JPEG、GIF、BMP、WebP 等常见图片格式

            【返回内容】
            - 图片中识别到的文字内容
            - 如果是截图，会返回界面上的关键文字
            - 如果是照片，会尝试理解并描述内容

            【使用建议】
            - 调用时必须提供图片URL和正确的MIME类型
            - image_type 示例：image/png、image/jpeg、image/jpg
            - 如果图片中无文字，会返回"未识别到有效文字内容"
            """;
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "image_url", Map.of(
                                "type", "string",
                                "description", "图片的URL地址，可以是本地存储路径或网络可访问的URL"
                        ),
                        "image_type", Map.of(
                                "type", "string",
                                "description", "图片的MIME类型，如：image/png、image/jpeg、image/jpg"
                        )
                ),
                "required", java.util.List.of("image_url", "image_type")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String imageUrl = (String) params.get("image_url");
        String imageType = (String) params.get("image_type");

        if (imageUrl == null || imageUrl.isBlank()) {
            return ToolResult.fail("image_url参数不能为空");
        }
        if (imageType == null || imageType.isBlank()) {
            return ToolResult.fail("image_type参数不能为空");
        }
        if (!imageRecognitionService.isImageFile(imageType)) {
            return ToolResult.fail("不支持的图片格式: " + imageType);
        }

        String result = imageRecognitionService.recognizeImageContent(imageUrl, imageType);
        return ToolResult.ok(result != null ? result : "图片中未识别到有效文字内容");
    }
}
