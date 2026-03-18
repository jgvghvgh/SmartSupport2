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

}