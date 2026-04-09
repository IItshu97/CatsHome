package com.catshome.smarthome.mqtt;

import com.catshome.smarthome.config.MqttProperties;
import com.catshome.smarthome.exception.InvalidOperationException;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Manages the Eclipse Paho MQTT client lifecycle.
 * Subscribes to all device topic patterns on startup and re-subscribes after reconnect.
 * Implements {@link MqttPublisher} for outbound commands.
 */
@Component
public class MqttClientWrapper implements MqttPublisher, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(MqttClientWrapper.class);

    /**
     * Wildcard subscriptions per device_contract.md §4:
     *   +/+/+         — main state updates (3-segment topics)
     *   +/+/+/status  — LWT online/offline
     *   +/+/+/state   — shutter position state
     *   +/+/+/raw     — smoke sensor raw ADC (informational, QoS 0)
     */
    private static final String[] SUBSCRIBE_TOPICS = {
            "+/+/+",
            "+/+/+/status",
            "+/+/+/state",
            "+/+/+/raw"
    };
    private static final int[] SUBSCRIBE_QOS = {1, 1, 1, 0};

    private final MqttProperties props;
    private final MqttMessageHandler handler;
    private IMqttClient client;

    public MqttClientWrapper(MqttProperties props, MqttMessageHandler handler) {
        this.props = props;
        this.handler = handler;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            client = new MqttClient(props.brokerUrl(), props.clientId());
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    if (reconnect) {
                        log.info("MQTT reconnected to {}, re-subscribing", serverURI);
                        subscribeAll();
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT connection lost: {}", cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    try {
                        handler.handle(topic, new String(message.getPayload(), StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        log.error("Error processing MQTT message on topic {}: {}", topic, e.getMessage(), e);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            if (props.username() != null && !props.username().isBlank()) {
                options.setUserName(props.username());
                options.setPassword(props.password().toCharArray());
            }

            client.connect(options);
            subscribeAll();
            log.info("MQTT connected to {}", props.brokerUrl());
        } catch (MqttException e) {
            log.error("Failed to connect to MQTT broker at {}: {}", props.brokerUrl(), e.getMessage());
        }
    }

    private void subscribeAll() {
        try {
            client.subscribe(SUBSCRIBE_TOPICS, SUBSCRIBE_QOS);
            log.debug("MQTT subscribed to {} wildcard patterns", SUBSCRIBE_TOPICS.length);
        } catch (MqttException e) {
            log.error("MQTT subscription failed: {}", e.getMessage());
        }
    }

    @Override
    public void publish(String topic, String payload) {
        if (client == null || !client.isConnected()) {
            throw new InvalidOperationException("MQTT broker not connected");
        }
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            client.publish(topic, message);
            log.debug("MQTT published to {}: {}", topic, payload);
        } catch (MqttException e) {
            throw new InvalidOperationException("Failed to publish MQTT message: " + e.getMessage());
        }
    }

    @PreDestroy
    public void disconnect() {
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                client.close();
                log.info("MQTT client disconnected");
            } catch (MqttException e) {
                log.warn("Error disconnecting MQTT client: {}", e.getMessage());
            }
        }
    }
}