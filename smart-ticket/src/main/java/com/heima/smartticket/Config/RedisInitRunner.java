package com.heima.smartticket.Config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public abstract class RedisInitRunner implements ApplicationRunner {

    @Autowired
    private RedisTemplate<String,Object> redisTemplate;



    @Override
    public void run(ApplicationArguments args) {

        Boolean lock = redisTemplate.opsForValue().setIfAbsent(
                "init:agent_load",
                "1",
                Duration.ofMinutes(1)
        );

        if(Boolean.FALSE.equals(lock)){
            return;
        }

    }



}