package com.heima.smartai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeMapper {

    @Select("""
        SELECT content
        FROM knowledge_base
        ORDER BY embedding <-> #{vector}
        LIMIT #{topK}
    """)
    List<String> search(
            @Param("vector") String vector,
            @Param("topK") int topK
    );

    /**
     * PostgreSQL全文检索（BM25实现）
     */
    @Select("""
        SELECT content
        FROM knowledge_base
        WHERE to_tsvector('simple', content) @@ to_tsquery('simple', #{query})
        ORDER BY ts_rank(to_tsvector('simple', content), to_tsquery('simple', #{query})) DESC
        LIMIT #{topK}
    """)
    List<String> searchByFullText(
            @Param("query") String tsQuery,
            @Param("topK") int topK
    );

}