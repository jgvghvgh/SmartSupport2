package com.heima.smartai.agent.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP Client配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "mcp.client")
public class McpClientConfig {

    private List<McpServerConfig> servers;

    @Data
    public static class McpServerConfig {
        private String name;
        private String url;
        private boolean enabled = true;
    }
}
