package com.heima.smartai.AiService;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class KnowledgeImportService {

    @Autowired
    EmbeddingStore<TextSegment> store;

    @Autowired
    EmbeddingModel embeddingModel;

    /**
     * 导入知识文本到向量库
     * @param text 知识文本
     * @return 导入的文档 ID 列表
     */
    public List<String> importDoc(String text) {
        // 1 文档切分
        List<TextSegment> segments = DocumentSplitters.recursive(500, 100)
                .split(Document.from(text));

        // 2 embedding
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 3 存入向量库
        List<String> ids = store.addAll(embeddings, segments);
        log.info("知识导入完成，条数={}", ids.size());
        return ids;
    }

    /**
     * 根据 ID 删除知识
     * @param id 文档 ID
     */
    public void deleteById(String id) {
        store.removeAll(List.of(id));
        log.info("知识删除完成, id={}", id);
    }

    /**
     * 搜索知识库
     * @param query 查询文本
     * @param topK 返回条数
     * @return 匹配的文本列表
     */
    public List<String> search(String query, int topK) {
        Embedding embedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(topK)
                .build();
        return store.search(request).matches()
                .stream()
                .map(m -> m.embedded().text())
                .toList();
    }
}
