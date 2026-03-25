package com.heima.smartai.rag;

import com.heima.smartai.cache.CacheWarmupService;
import com.heima.smartai.cache.RetrievalCacheService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class VectorRetriever {

    @Autowired
    private RetrievalCacheService cacheService;

    @Autowired
    private CacheWarmupService cacheWarmupService;

    @Autowired
    EmbeddingStore<TextSegment> store;

    @Autowired
    EmbeddingModel embeddingModel;

    public List<String> search(String query) {
        String hash = DigestUtils.md5DigestAsHex(query.getBytes());

        // 1 查询缓存
        List<String> cacheDocs = cacheService.get(hash);
        if (cacheDocs != null && !cacheDocs.isEmpty()) {
            // 检查是否逻辑过期，是则触发异步刷新（由 CacheWarmupService 统一处理）
            if (cacheService.isLogicalExpired(hash)) {
                log.debug("检索缓存逻辑过期，触发异步刷新, queryHash={}", hash);
                cacheWarmupService.refreshExpiredCache(query, null);
            }
            return cacheDocs;
        }

        // 2 向量化
        Embedding embedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(3)
                .build();

        // 3 向量检索
        List<String> docs = store.search(request)
                .matches()
                .stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.toList());

        // 4 写缓存
        cacheService.set(hash, docs);

        return docs;
    }
}
