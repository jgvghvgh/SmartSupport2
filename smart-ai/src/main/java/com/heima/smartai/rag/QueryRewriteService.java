package com.heima.smartai.rag;

import com.heima.smartai.Config.AiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueryRewriteService {

    @Autowired
    private AiClient aiClient;

    public String rewrite(String question){

        String prompt = """
        请优化用户问题，使其更适合知识库搜索：
        %s
        """.formatted(question);

        List<Map<String,Object>> messages =
                new ArrayList<>();

        Map<String,Object> msg =
                new HashMap<>();

        msg.put("role","user");
        msg.put("content",prompt);

        messages.add(msg);

        String aiText = aiClient.chatRaw(messages);
        if (aiText == null || aiText.isBlank()) {
            return question; // 兜底返回原始问题
        }
        // 取 problemAnalysis 部分作为改写结果
        String[] parts = aiText.split(“解决建议[:：]”);
        return parts.length > 0
                ? parts[0].replace(“问题分析：”, “”).trim()
                : aiText;
    }

}