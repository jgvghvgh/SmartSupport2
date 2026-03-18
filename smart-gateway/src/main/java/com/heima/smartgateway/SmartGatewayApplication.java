package com.heima.smartgateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.List;

@SpringBootApplication
@EnableDiscoveryClient
public class SmartGatewayApplication {


    public static void main(String[] args) {
        SpringApplication.run(SmartGatewayApplication.class, args);
    }

    @Autowired
    private DiscoveryClient discoveryClient;

    @PostConstruct
    public void showInstances() {
        List<ServiceInstance> instances = discoveryClient.getInstances("smart-auth");
        instances.forEach(System.out::println);
    }

}
