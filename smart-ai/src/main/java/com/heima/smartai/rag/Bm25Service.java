package com.heima.smartai.rag;

import com.heima.smartai.mapper.KnowledgeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * BM25关键词检索服务
 * BM25是一种经典的信息检索算法，用于评估文档与查询词之间的相关性
 */
@Slf4j
@Service
public class Bm25Service {

    private static final double K1 = 1.5;
    private static final double B = 0.75;
    private static final int TOP_K = 20;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    /**
     * 使用PostgreSQL全文检索实现BM25
     *
     * @param query 用户查询
     * @return BM25检索结果（按相关性排序的文档列表）
     */
    public List<String> search(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        try {
            // 将用户查询转换为全文检索查询格式
            String tsQuery = toTsQuery(query);

            // 执行全文检索，按相关性排序
            List<String> results = knowledgeMapper.searchByFullText(tsQuery, TOP_K);

            log.debug("BM25检索结果数量: {}", results.size());
            return results;
        } catch (Exception e) {
            log.error("BM25检索异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 将用户查询转换为PostgreSQL全文检索的tsquery格式
     */
    private String toTsQuery(String query) {
        // 分词并构建tsquery（取前5个词）
        String[] words = query.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(words.length, 5); i++) {
            if (i > 0) {
                sb.append(" & ");
            }
            sb.append(words[i]).append(":*");
        }
        return sb.toString();
    }
}
