package com.heima.smartai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiAnalysisResult {
    private String problemAnalysis;   // 问题分析
    private String referenceReply;    // 参考回复
}
