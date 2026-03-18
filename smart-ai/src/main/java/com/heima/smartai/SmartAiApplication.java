package com.heima.smartai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.heima.smartai.client")
public class SmartAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartAiApplication.class, args);
    }

}
