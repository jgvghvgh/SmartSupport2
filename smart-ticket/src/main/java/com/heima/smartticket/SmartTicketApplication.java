package com.heima.smartticket;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(scanBasePackages = {"com.heima.smartticket", "com.heima.smartauth.Service" },
        exclude = {
                org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
        }
)
@ComponentScan(basePackages = {"com.heima.smartticket", "com.heima.smartcommon","com.heima.smartauth.WebSocket",})
@EnableDiscoveryClient
public class SmartTicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartTicketApplication.class, args);
    }

}