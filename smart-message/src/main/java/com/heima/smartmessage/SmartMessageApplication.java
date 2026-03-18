package com.heima.smartmessage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class SmartMessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartMessageApplication.class, args);
    }

}
