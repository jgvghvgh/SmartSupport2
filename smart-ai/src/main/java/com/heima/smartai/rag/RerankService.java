package com.heima.smartai.rag;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RerankService {

    public List<String> rerank(String query, List<String> docs){

        // 简化版
        return docs.stream()
                .limit(3)
                .collect(Collectors.toList());
    }
}