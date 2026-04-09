package com.catshome.smarthome.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jwt")
public record JwtProperties(
        String secret,
        long expirationMs
) {}