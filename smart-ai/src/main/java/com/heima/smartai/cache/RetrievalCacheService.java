package com.heima.smartai.cache;

import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RetrievalCacheService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String PREFIX = "rag:retrieval:";
    private static final String VERSION_KEY = "rag:retrieval:version";
    public List<String> get(String queryHash){

        String version = getVersion();

        String key = PREFIX + version + ":" + queryHash;

        String json = redisTemplate.opsForValue().get(key);

        if(json == null){
            return null;
        }

        return JSON.parseArray(json,String.class);
    }

    public void set(String queryHash, List<String> docIds){

        String version = getVersion();

        String key = PREFIX + version + ":" + queryHash;


        String json = JSON.toJSONString(docIds);

        redisTemplate.opsForValue()
                .set(key,json,30, TimeUnit.DAYS);
    }
    private String getVersion(){

        String version = redisTemplate.opsForValue()
                .get(VERSION_KEY);

        if(version == null){

            version = "v1";

            redisTemplate.opsForValue()
                    .set(VERSION_KEY,version);
        }

        return version;
    }
}