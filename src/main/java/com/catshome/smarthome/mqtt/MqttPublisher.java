package com.catshome.smarthome.mqtt;

/**
 * Outbound port: publishes a message to the MQTT broker.
 */
public interface MqttPublisher {
    void publish(String topic, String payload);
}
