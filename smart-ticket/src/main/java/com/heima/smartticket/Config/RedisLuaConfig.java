package com.heima.smartticket.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisLuaConfig {
    private static final String ALLOCATE_CS_LUA = """
            local agentList = redis.call('ZRANGE', KEYS[1], 0, 4)
            
            for i, agentId in ipairs(agentList) do
            
                local onlineKey = KEYS[2] .. agentId
            
                if redis.call('EXISTS', onlineKey) == 1 then
            
                    redis.call('ZINCRBY', KEYS[1], 1, agentId)
            
                    redis.call('SET', KEYS[3] .. ARGV[1], agentId)
            
                    return agentId
                end
            end
            
            return nil
            """;

    @Bean
    public DefaultRedisScript<Long> allocateCsScript() {

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();

        script.setScriptText(ALLOCATE_CS_LUA);

        script.setResultType(Long.class);

        return script;
    }
}