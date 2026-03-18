package com.heima.smartai.Config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data

@Configuration
@ConfigurationProperties(prefix = "ai")
public class AIConfig {

    /**
     * AI 提供商，例如 qianwen / openai
     */
    private String provider;

    /**
     * 接口地址
     */
    private String apiUrl;

    /**
     * 阿里云 AccessKeyId
     */
    private String accessKeyId;

    /**
     * 阿里云 AccessKeySecret
     */
    private String accessKeySecret;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * 温度参数（0-1）
     */
    private Double temperature = 0.7;


}
