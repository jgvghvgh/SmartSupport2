package com.heima.smartai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 多路召回服务
 *
 * 三路召回：
 * 1. 向量检索（基础，一路）- 使用Embedding + 余弦相似度
 * 2. BM25关键词检索 - 使用PostgreSQL全文检索
 * 3. Query改写 + HyDE + 向量检索（合并去重）
 *
 * 最后使用RRF（Reciprocal Rank Fusion，倒数排名融合）算法融合三路结果
 */
@Slf4j
@Service
public class MultiRetrievalService {

    private static final int RRF_K = 60;  // RRF算法常量

    @Autowired
    private VectorRetriever vectorRetriever;

    @Autowired
    private Bm25Service bm25Service;

    @Autowired
    private QueryRewriteService queryRewriteService;

    @Autowired
    private HydeService hydeService;

    /**
     * 多路召回
     *
     * @param query 用户原始问题
     * @return 融合后的文档列表（按RRF分数排序）
     */
    public List<String> multiSearch(String query) {
        log.info("开始三路召回，原始query: {}", query);

        // 并行执行三路召回
        CompletableFuture<List<String>> vectorFuture = CompletableFuture.supplyAsync(
            () -> {
                log.debug("召回路径1-向量检索执行");
                return vectorRetriever.search(query);
            });

        CompletableFuture<List<String>> bm25Future = CompletableFuture.supplyAsync(
            () -> {
                log.debug("召回路径2-BM25检索执行");
                return bm25Service.search(query);
            });

        CompletableFuture<List<String>> rewriteHydeFuture = CompletableFuture.supplyAsync(
            () -> {
                log.debug("召回路径3-Query改写+HyDE+向量检索执行");
                // Query改写 + 向量检索
                String rewritten = queryRewriteService.rewrite(query);
                List<String> rewriteDocs = vectorRetriever.search(rewritten);
                // HyDE + 向量检索
                String hypoDoc = hydeService.generateHypotheticalDoc(query);
                List<String> hydeDocs = vectorRetriever.search(hypoDoc);
                // 合并去重
                return mergeAndDedup(rewriteDocs, hydeDocs);
            });

        // 等待所有结果
        CompletableFuture.allOf(vectorFuture, bm25Future, rewriteHydeFuture).join();

        try {
            List<String> vectorDocs = vectorFuture.get();
            List<String> bm25Docs = bm25Future.get();
            List<String> rewriteHydeDocs = rewriteHydeFuture.get();

            log.info("三路召回结果数量: 向量={}, BM25={}, 改写+HyDE={}",
                vectorDocs.size(), bm25Docs.size(), rewriteHydeDocs.size());

            // RRF融合
            List<String> fused = rrfFusion(vectorDocs, bm25Docs, rewriteHydeDocs);
            log.info("RRF融合后文档数量: {}", fused.size());

            return fused;
        } catch (Exception e) {
            log.error("多路召回异常: {}", e.getMessage(), e);
            // 兜底返回向量检索结果
            return vectorRetriever.search(query);
        }
    }

    /**
     * RRF（Reciprocal Rank Fusion）倒数排名融合算法
     *
     * RRF(d) = Σ 1/(k + rank(d))
     * 其中k是常量（默认60），rank(d)是文档d在各个召回路径中的排名
     *
     * @param docLists 多个召回路径的文档列表
     * @return 融合后的文档列表
     */
    private List<String> rrfFusion(List<String>... docLists) {
        // 计算每个文档的RRF分数
        Map<String, Double> rrfScores = new HashMap<>();

        for (List<String> docs : docLists) {
            if (docs == null || docs.isEmpty()) {
                continue;
            }
            // 遍历文档列表，计算排名（从1开始）
            for (int rank = 0; rank < docs.size(); rank++) {
                String doc = docs.get(rank);
                double score = 1.0 / (RRF_K + rank + 1);
                rrfScores.merge(doc, score, Double::sum);
            }
        }

        // 按RRF分数降序排序
        return rrfScores.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * 合并多个文档列表并去重
     */
    private List<String> mergeAndDedup(List<String>... docLists) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (List<String> docs : docLists) {
            if (docs != null) {
                unique.addAll(docs);
            }
        }
        return new ArrayList<>(unique);
    }
}
