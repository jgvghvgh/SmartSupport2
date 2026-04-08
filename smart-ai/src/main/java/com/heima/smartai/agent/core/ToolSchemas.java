package com.heima.smartai.agent.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 工具 Schema 定义
 * 用于告诉 LLM 有哪些工具可用、参数是什么
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolSchemas {

    /**
     * 单个工具的 Schema
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolSchema {
        /** 工具类型，固定为 function */
        @Builder.Default
        private String type = "function";

        /** 工具定义 */
        private Function function;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Function {
            /** 工具唯一名称 */
            private String name;

            /** 工具描述 */
            private String description;

            /** JSON Schema 格式的参数定义 */
            private Map<String, Object> parameters;
        }
    }

    /** 工具列表 */
    private List<ToolSchema> tools;

    /**
     * 生成 JSON 格式的 tools 参数
     * 用于直接传递给 LLM API
     */
    public String toJson() {
        return com.alibaba.fastjson2.JSON.toJSONString(this);
    }
}
