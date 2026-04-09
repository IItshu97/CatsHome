package com.catshome.smarthome.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mqtt")
public record MqttProperties(
        String brokerUrl,
        String clientId,
        String username,
        String password
) {}