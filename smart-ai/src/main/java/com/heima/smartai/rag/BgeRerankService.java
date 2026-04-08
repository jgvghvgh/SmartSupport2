package com.heima.smartai.rag;

import com.heima.smartai.Config.AIConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BGE-Reranker 重排序服务
 * 使用专门的rerank模型对召回文档进行重排序
 *
 *
 */
@Slf4j
@Service
public class BgeRerankService {

    @Autowired
    private AIConfig aiConfig;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 使用BGE-Reranker进行重排序
     *
     * @param query 用户问题
     * @param documents 待排序的文档列表
     * @param topN 返回前topN个
     * @return 重排序后的文档列表
     */
    public List<String> rerank(String query, List<String> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            log.warn("待重排序文档为空");
            return new ArrayList<>();
        }

        if (documents.size() <= topN) {
            log.debug("文档数量({}) <= topN({})，无需重排序", documents.size(), topN);
            return documents;
        }

        log.info("开始BGE-Rerank重排序，query长度: {}, 文档数: {}, topN: {}",
            query.length(), documents.size(), topN);

        try {
            // 根据配置的API类型选择不同的调用方式
            String rerankApiUrl = aiConfig.getRerankApiUrl();
            if (rerankApiUrl == null || rerankApiUrl.isBlank()) {
                log.warn("未配置rerank API，使用简单截断作为降级方案");
                return documents.stream().limit(topN).collect(Collectors.toList());
            }

            // 构建rerank请求
            Map<String, Object> body = new HashMap<>();
            body.put("model", aiConfig.getRerankModel());
            body.put("query", query);
            body.put("documents", documents);
            body.put("top_n", topN);
            body.put("return_documents", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + aiConfig.getAccessKeyId());
            if (aiConfig.getProvider() != null && aiConfig.getProvider().contains("dashscope")) {
                headers.set("X-DashScope-Model", aiConfig.getRerankModel());
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.debug("调用rerank API: {}", rerankApiUrl);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                rerankApiUrl, request, Map.class);

            List<String> results = parseRerankResponse(response.getBody());
            log.info("BGE-Rerank完成，返回{}个文档", results.size());
            return results;

        } catch (Exception e) {
            log.error("BGE-Rerank调用失败: {}, 使用降级方案", e.getMessage());
            // 降级：简单返回前topN个
            return documents.stream().limit(topN).collect(Collectors.toList());
        }
    }

    /**
     * 解析rerank API响应
     * 支持阿里云DashScope格式
     */
    @SuppressWarnings("unchecked")
    private List<String> parseRerankResponse(Map<String, Object> body) {
        if (body == null) {
            return new ArrayList<>();
        }

        List<String> results = new ArrayList<>();

        try {
            // 尝试DashScope格式
            Map<String, Object> output = (Map<String, Object>) body.get("output");
            if (output != null) {
                List<Map<String, Object>> rankings = (List<Map<String, Object>>) output.get("rankings");
                if (rankings != null) {
                    for (Map<String, Object> item : rankings) {
                        Map<String, Object> doc = (Map<String, Object>) item.get("document");
                        if (doc != null) {
                            results.add((String) doc.get("text"));
                        }
                    }
                    return results;
                }
            }

            // 尝试标准rerank格式
            List<Map<String, Object>> rankings = (List<Map<String, Object>>) body.get("results");
            if (rankings != null) {
                for (Map<String, Object> item : rankings) {
                    Map<String, Object> doc = (Map<String, Object>) item.get("document");
                    if (doc != null) {
                        results.add((String) doc.get("text"));
                    } else {
                        // 直接是文本的情况
                        Object text = item.get("text");
                        if (text != null) {
                            results.add(text.toString());
                        }
                    }
                }
                return results;
            }

            // 尝试results数组直接是字符串的情况
            Object rawResults = body.get("results");
            if (rawResults instanceof List) {
                List<?> rawList = (List<?>) rawResults;
                if (!rawList.isEmpty() && rawList.get(0) instanceof String) {
                    return (List<String>) rawList;
                }
            }

            log.warn("无法解析rerank响应格式: {}", body.keySet());

        } catch (Exception e) {
            log.error("解析rerank响应失败: {}", e.getMessage());
        }

        return results;
    }
}
