package com.cartmate.cartmate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class OrderServiceClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
