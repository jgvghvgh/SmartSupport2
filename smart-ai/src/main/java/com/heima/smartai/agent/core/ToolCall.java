package com.heima.smartai.agent.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 结构化工具调用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /** 调用的工具名称 */
    private String name;

    /** 调用ID（用于多工具调用场景） */
    private String callId;

    /** 工具参数 */
    private Map<String, Object> arguments;

    /** 生成此调用的 LLM 思考过程（可选） */
    private String thought;
}
