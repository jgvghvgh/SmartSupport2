package com.heima.smartai.Config;

import com.heima.smartai.model.AiAnalysisResult;
import com.heima.smartai.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 客户端，封装与 AI 大模型的交互
 * 核心方法 chat(List<Message>) 支持多模态消息上传
 */
@Slf4j
@Component
public class AiClient {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AIConfig aiConfig;

    /**
     * 核心 chat 方法，支持多模态消息上传
     * @param messages 消息列表，支持文本和图片
     * @return AI 原始响应文本
     */
    public String chat(List<Message> messages) {
        List<Map<String, Object>> formattedMessages = messages.stream()
                .map(this::formatMessage)
                .collect(Collectors.toList());

        Map<String, Object> body = new HashMap<>();
        body.put("model", aiConfig.getModel());
        body.put("input", formattedMessages);
        body.put("parameters", Map.of(
                "temperature", aiConfig.getTemperature(),
                "max_output_tokens", 512
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + aiConfig.getAccessKeyId());
        headers.set("X-DashScope-Model", aiConfig.getModel());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> aiResponse;
        try {
            aiResponse = restTemplate.postForEntity(aiConfig.getApiUrl(), request, Map.class);
        } catch (Exception e) {
            log.error("AI调用异常：{}", e.getMessage());
            return null;
        }

        Map<String, Object> resBody = aiResponse.getBody();
        if (resBody == null) {
            return null;
        }

        try {
            Map<String, Object> output = (Map<String, Object>) resBody.get("output");
            if (output != null && output.containsKey("text")) {
                return (String) output.get("text");
            }
        } catch (Exception e) {
            log.error("解析AI响应失败：{}", e.getMessage());
        }

        return null;
    }

    /**
     * 格式化 Message 为 API 所需格式
     */
    private Map<String, Object> formatMessage(Message message) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", message.getRole());

        if (message.getImages() != null && !message.getImages().isEmpty()) {
            // 多模态消息格式：content 为对象列表
            List<Object> contentList = new ArrayList<>();
            // 文本部分
            if (message.getContent() != null && !message.getContent().isBlank()) {
                contentList.add(Map.of("text", message.getContent()));
            }
            // 图片部分
            for (Message.ImageContent img : message.getImages()) {
                contentList.add(Map.of(
                        "image", Map.of(
                                "url", img.getUrl(),
                                "imageType", img.getImageType()
                        )
                ));
            }
            msg.put("content", contentList);
        } else {
            // 纯文本消息
            msg.put("content", message.getContent());
        }

        return msg;
    }

    /**
     * 解析 AI 响应，返回结构化结果
     * @param aiText AI 原始响应
     * @return 解析后的分析结果
     */
    public AiAnalysisResult parseResponse(String aiText) {
        if (aiText == null || aiText.isBlank()) {
            return new AiAnalysisResult("AI返回为空", "无法获取AI分析结果");
        }

        String[] parts = aiText.split("解决建议[:：]");
        String analysis = parts.length > 0
                ? parts[0].replace("问题分析：", "").trim()
                : aiText;
        String suggestion = parts.length > 1
                ? parts[1].trim()
                : "请人工进一步核实。";

        return new AiAnalysisResult(analysis, suggestion);
    }

    /**
     * 直接返回模型 output.text，不做结构化解析
     * 用于意图分类等需要原始文本的场景
     */
    public String chatRaw(List<Map<String, Object>> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", aiConfig.getModel());
        body.put("input", messages);
        body.put("parameters", Map.of(
                "temperature", aiConfig.getTemperature(),
                "max_output_tokens", 512
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + aiConfig.getAccessKeyId());
        headers.set("X-DashScope-Model", aiConfig.getModel());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> aiResponse;
        try {
            aiResponse = restTemplate.postForEntity(aiConfig.getApiUrl(), request, Map.class);
        } catch (Exception e) {
            log.error("AI调用异常：{}", e.getMessage());
            return null;
        }

        Map<String, Object> resBody = aiResponse.getBody();
        if (resBody == null) {
            return null;
        }

        try {
            Map<String, Object> output = (Map<String, Object>) resBody.get("output");
            if (output != null && output.containsKey("text")) {
                return (String) output.get("text");
            }
        } catch (Exception e) {
            log.error("解析AI响应失败：{}", e.getMessage());
        }

        return null;
    }
}
