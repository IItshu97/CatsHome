package com.catshome.smarthome.unit;

import com.catshome.smarthome.entity.DeviceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeviceTypeTest {

    @ParameterizedTest
    @EnumSource(DeviceType.class)
    void topicPrefix_isLowercaseEnumName(DeviceType type) {
        assertEquals(type.name().toLowerCase(), type.topicPrefix());
    }

    @Test
    void hasReadings_trueForTimeSeries() {
        Set<DeviceType> expected = Set.of(
                DeviceType.TEMPERATURE, DeviceType.THERMOSTAT,
                DeviceType.LUX, DeviceType.ENERGY);

        for (DeviceType type : DeviceType.values()) {
            assertEquals(expected.contains(type), type.hasReadings(),
                    type + " hasReadings mismatch");
        }
    }

    @Test
    void isPriorityAlarm_trueOnlyForSmokeAndFlood() {
        assertTrue(DeviceType.SMOKE.isPriorityAlarm());
        assertTrue(DeviceType.FLOOD.isPriorityAlarm());

        for (DeviceType type : DeviceType.values()) {
            if (type != DeviceType.SMOKE && type != DeviceType.FLOOD) {
                assertFalse(type.isPriorityAlarm(), type + " should not be a priority alarm");
            }
        }
    }
}
