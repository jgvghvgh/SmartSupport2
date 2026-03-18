package com.heima.smartai.rag;

import java.util.List;

public class PromptBuilder {

    public static String build(
            String question,
            List<String> docs){

        String knowledge =
                String.join("\n",docs);

        return """
        你是一个客服专家，请根据知识库回答用户问题。

        知识库：
        %s

        用户问题：
        %s

        请回答：
        1. 问题分析
        2. 解决建议
        """.formatted(knowledge,question);
    }
}