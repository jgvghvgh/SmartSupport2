package com.heima.smartai.AiService;

import com.heima.smartai.Config.AiClient;
import com.heima.smartai.model.AiAnalysisResult;
import com.heima.smartai.rag.PromptBuilder;
import com.heima.smartai.rag.QueryRewriteService;
import com.heima.smartai.rag.RerankService;
import com.heima.smartai.rag.VectorRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagPipelineService {

    @Autowired
    private QueryRewriteService queryRewriteService;

    @Autowired
    private VectorRetriever vectorRetriever;

    @Autowired
    private RerankService rerankService;

    @Autowired
    private AiClient aiClient;

    public AiAnalysisResult chat(String question){

        // 1 QueryRewrite
        String rewriteQuery =
                queryRewriteService.rewrite(question);

        // 2 Vector Search (embedding + search)
        List<String> docs =
                vectorRetriever.search(rewriteQuery);

        // 3 Rerank
        List<String> topDocs =
                rerankService.rerank(rewriteQuery,docs);

        // 4 Prompt
        String prompt =
                PromptBuilder.build(question,topDocs);

        // 5 AI
        List<Map<String,Object>> messages =
                buildMessages(prompt);

        return aiClient.chat(messages);
    }

    private List<Map<String,Object>> buildMessages(String prompt){

        List<Map<String,Object>> messages =
                new ArrayList<>();

        Map<String,Object> msg =
                new HashMap<>();

        msg.put("role","user");
        msg.put("content",prompt);

        messages.add(msg);

        return messages;
    }
}