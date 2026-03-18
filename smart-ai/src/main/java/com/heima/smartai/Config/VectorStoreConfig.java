package com.heima.smartai.Config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfig {

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {

        return PgVectorEmbeddingStore.builder()
                .host("localhost")
                .port(5432)
                .database("rag")
                .user("postgres")
                .password("123456")
                .table("knowledge")
                .dimension(1536)
                .build();
    }

}