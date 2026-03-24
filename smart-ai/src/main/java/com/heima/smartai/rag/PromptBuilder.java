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

    public static String buildCautious(
            String question,
            List<String> docs
    ){
        String knowledge =
                String.join("\n",docs);

        return """
        你是一个客服专家，请根据知识库尽量回答用户问题。
        如果知识库不足以支持结论，请不要胡编，应该明确告诉用户需要客服补充/请重新描述。

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