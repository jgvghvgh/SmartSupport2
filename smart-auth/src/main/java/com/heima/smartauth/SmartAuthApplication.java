package com.heima.smartauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication

@EnableDiscoveryClient
public class SmartAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartAuthApplication.class, args);
    }

}
