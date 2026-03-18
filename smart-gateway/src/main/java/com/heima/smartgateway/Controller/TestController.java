package com.heima.smartgateway.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.List;

@RestController
public class TestController {

    @Autowired
    private ReactiveDiscoveryClient discoveryClient;

    @GetMapping("/test-instances")
    public Mono<List<ServiceInstance>> testInstances() {
        return discoveryClient.getInstances("smart-auth").collectList();
    }
}
