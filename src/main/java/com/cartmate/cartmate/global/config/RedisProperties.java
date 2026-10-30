package com.cartmate.cartmate.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "custom.redis")
public record RedisProperties(String password, int database, Node cache, Node core) {

    public record Node(String host, int port) {
    }
}
