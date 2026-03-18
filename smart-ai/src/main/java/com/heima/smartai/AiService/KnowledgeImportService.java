package com.heima.smartai.AiService;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeImportService {

    @Autowired
    EmbeddingStore<TextSegment> store;

    @Autowired
    EmbeddingModel embeddingModel;

    public void importDoc(String text){

        // 1 文档切分
        List<TextSegment> segments =
                DocumentSplitters.recursive(500,100)
                        .split(Document.from(text));

        // 2 embedding
        List<Embedding> embeddings =
                embeddingModel.embedAll(segments)
                        .content();

        // 3 存入向量库
        store.addAll(embeddings,segments);

    }

}