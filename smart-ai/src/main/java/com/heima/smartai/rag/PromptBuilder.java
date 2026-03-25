package com.heima.smartai.rag;

import java.util.List;

public class PromptBuilder {

    public static String build(String question, List<String> docs, String imageAnalysisResult) {
        String knowledge = String.join("\n", docs);
        String imageSection = buildImageSection(imageAnalysisResult);
        return """
        你是一个客服专家，请根据知识库回答用户问题。

        知识库：
        %s
        %s

        用户问题：
        %s

        请回答：
        1. 问题分析
        2. 解决建议
        """.formatted(knowledge, imageSection, question);
    }

    public static String build(String question, List<String> docs) {
        return build(question, docs, null);
    }

    public static String buildCautious(String question, List<String> docs, String imageAnalysisResult) {
        String knowledge = String.join("\n", docs);
        String imageSection = buildImageSection(imageAnalysisResult);
        return """
        你是一个客服专家，请根据知识库尽量回答用户问题。
        如果知识库不足以支持结论，请不要胡编，应该明确告诉用户需要客服补充/请重新描述。

        知识库：
        %s
        %s

        用户问题：
        %s

        请回答：
        1. 问题分析
        2. 解决建议
        """.formatted(knowledge, imageSection, question);
    }

    public static String buildCautious(String question, List<String> docs) {
        return buildCautious(question, docs, null);
    }

    private static String buildImageSection(String imageAnalysisResult) {
        if (imageAnalysisResult == null || imageAnalysisResult.isBlank()) {
            return "";
        }
        return "\n\n用户上传的图片内容：\n" + imageAnalysisResult + "\n";
    }
}
