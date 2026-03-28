package com.heima.smartai.agent.tools;

import com.heima.smartai.agent.core.SimpleTool;
import com.heima.smartai.AiService.ImageRecognitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 图片识别工具
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
        return "当用户上传了图片，需要识别图片中的文字内容或理解图片信息时使用。参数：image_url(必填), image_type(必填，如image/png)";
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
