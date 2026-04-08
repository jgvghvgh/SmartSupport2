package com.heima.smartai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 多路召回服务
 * 并行执行多条召回路径，然后合并去重
 *
 * 召回路径：
 * 1. 原始query
 * 2. 改写query (QueryRewriteService)
 * 3. HyDE假设答案 (HydeService)
 */
@Slf4j
@Service
public class MultiRetrievalService {

    @Autowired
    private QueryRewriteService queryRewriteService;

    @Autowired
    private HydeService hydeService;

    @Autowired
    private VectorRetriever vectorRetriever;

    /**
     * 多路召回：并行执行3条召回路径
     *
     * @param query 用户原始问题
     * @return 合并去重后的文档列表
     */
    public List<String> multiSearch(String query) {
        log.info("开始多路召回，原始query: {}", query);

        // 并行执行三个召回任务
        CompletableFuture<List<String>> rawFuture = CompletableFuture.supplyAsync(
            () -> {
                log.debug("召回路径1-原始Query执行");
                return vectorRetriever.search(query);
            });

        CompletableFuture<List<String>> rewriteFuture = CompletableFuture.supplyAsync(() -> {
            log.debug("召回路径2-Query改写执行");
            String rewritten = queryRewriteService.rewrite(query);
            log.debug("改写后query: {}", rewritten);
            return vectorRetriever.search(rewritten);
        });

        CompletableFuture<List<String>> hydeFuture = CompletableFuture.supplyAsync(() -> {
            log.debug("召回路径3-HyDE假设文档执行");
            String hypoDoc = hydeService.generateHypotheticalDoc(query);
            log.debug("HyDE假设文档: {}", hypoDoc);
            return vectorRetriever.search(hypoDoc);
        });

        // 等待所有结果
        CompletableFuture.allOf(rawFuture, rewriteFuture, hydeFuture).join();

        try {
            List<String> rawDocs = rawFuture.get();
            List<String> rewriteDocs = rewriteFuture.get();
            List<String> hydeDocs = hydeFuture.get();

            log.info("三路召回结果数量: 原始={}, 改写={}, HyDE={}",
                rawDocs.size(), rewriteDocs.size(), hydeDocs.size());

            // 合并去重（使用LinkedHashSet保持顺序）
            List<String> merged = mergeAndDedup(rawDocs, rewriteDocs, hydeDocs);
            log.info("合并去重后文档数量: {}", merged.size());

            return merged;
        } catch (Exception e) {
            log.error("多路召回异常: {}", e.getMessage(), e);
            // 兜底返回原始query结果
            return vectorRetriever.search(query);
        }
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
