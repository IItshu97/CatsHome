package com.catshome.smarthome.entity;

public enum DeviceType {
    LIGHT,
    DOOR,
    WINDOW,
    TEMPERATURE,
    MOTION,
    SMOKE,
    SHUTTER,
    THERMOSTAT,
    FLOOD,
    LUX,
    ENERGY;

    /** MQTT topic prefix (lowercase enum name). */
    public String topicPrefix() {
        return name().toLowerCase();
    }

    /**
     * Builds the MQTT topic: {@code {type}/{normalizedName}}.
     * Room ID is not encoded in the topic — room assignment lives exclusively in the server DB.
     */
    public String buildTopic(String deviceName) {
        return topicPrefix() + "/" + deviceName.toLowerCase().replace(' ', '_');
    }

    /** Whether this device type stores time-series readings. */
    public boolean hasReadings() {
        return this == TEMPERATURE || this == THERMOSTAT || this == LUX || this == ENERGY;
    }

    /** Whether this device type is a priority alarm device. */
    public boolean isPriorityAlarm() {
        return this == SMOKE || this == FLOOD;
    }
}