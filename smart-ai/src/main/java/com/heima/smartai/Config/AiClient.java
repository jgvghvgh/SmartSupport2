package com.heima.smartai.Config;
import com.heima.smartai.model.AiAnalysisResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiClient {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AIConfig aiConfig;

    public AiAnalysisResult chat(List<Map<String, Object>> messages) {

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
        // 5. 发送请求
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> aiResponse;
        try {
            aiResponse = restTemplate.postForEntity(aiConfig.getApiUrl(), request, Map.class);
        } catch (Exception e) {
            System.out.println("AI调用异常：" + e.getMessage());
            return new AiAnalysisResult("AI分析失败", "AI调用异常：" + e.getMessage());
        }

        // 6. 解析响应
        Map<String, Object> resBody = aiResponse.getBody();
        if (resBody == null) {
            return new AiAnalysisResult("AI返回为空", "无法获取AI分析结果");
        }

        // 通义千问的返回格式：
        // {
        //   "output": {
        //     "text": "你的回答内容..."
        //   }
        // }
        try {
            Map<String, Object> output = (Map<String, Object>) resBody.get("output");
            if (output != null && output.containsKey("text")) {
                String aiText = (String) output.get("text");

                // 分割结果（简单方式）
                String[] parts = aiText.split("解决建议[:：]");
                String analysis = parts.length > 0 ? parts[0].replace("问题分析：", "").trim() : aiText;
                String suggestion = parts.length > 1 ? parts[1].trim() : "请人工进一步核实。";

                return new AiAnalysisResult(analysis, suggestion);
            }
        } catch (Exception e) {
            System.out.println("解析AI响应失败：" + e.getMessage());
        }

        return new AiAnalysisResult("AI响应解析失败", "请人工分析。");


//        HttpEntity<Map<String, Object>> request =
//                new HttpEntity<>(body, headers);
//
//        ResponseEntity<Map> response =
//                restTemplate.postForEntity(
//                        aiConfig.getApiUrl(),
//                        request,
//                        Map.class
//                );
//
//        Map<String, Object> resBody = response.getBody();
//
//        if (resBody == null) {
//            return null;
//        }
//
//        Map<String, Object> output =
//                (Map<String, Object>) resBody.get("output");
//
//        return output == null ? null : (String) output.get("text");
    }
}