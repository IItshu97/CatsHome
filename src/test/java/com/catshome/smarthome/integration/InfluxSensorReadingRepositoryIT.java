package com.catshome.smarthome.integration;

import com.catshome.smarthome.AbstractContainerTest;
import com.catshome.smarthome.dto.SensorReadingPoint;
import com.catshome.smarthome.repository.InfluxSensorReadingRepository;
import com.influxdb.client.DeleteApi;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InfluxSensorReadingRepositoryIT extends AbstractContainerTest {

    private static final String ORG    = "catshome";
    private static final String BUCKET = "smarthome";
    private static final String TOKEN  = "test-admin-token";

    private InfluxDBClient client;
    private InfluxSensorReadingRepository repo;

    @BeforeEach
    void setUp() {
        String url = "http://" + INFLUXDB.getHost() + ":" + INFLUXDB.getMappedPort(8086);
        client = InfluxDBClientFactory.create(url, TOKEN.toCharArray());
        repo = new InfluxSensorReadingRepository(client, ORG, BUCKET);
    }

    @AfterEach
    void tearDown() {
        // Delete all data written during the test
        DeleteApi deleteApi = client.getDeleteApi();
        deleteApi.delete(
                OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC),
                "", BUCKET, ORG);
        client.close();
    }

    @Test
    void saveAndQuery_returnsReadingsInTimeRange() {
        Instant now = Instant.now();
        Long deviceId = 1L;

        repo.save(deviceId, "TEMPERATURE", 10L, now.minus(2, ChronoUnit.HOURS), "{\"temp\":20.0}");
        repo.save(deviceId, "TEMPERATURE", 10L, now.minus(1, ChronoUnit.HOURS), "{\"temp\":21.0}");
        repo.save(deviceId, "TEMPERATURE", 10L, now.minus(30, ChronoUnit.MINUTES), "{\"temp\":22.0}");

        List<SensorReadingPoint> result = repo.findByDeviceIdBetween(
                deviceId,
                now.minus(90, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.MINUTES));

        assertEquals(2, result.size());
    }

    @Test
    void saveAndQuery_orderedNewestFirst() {
        Instant now = Instant.now();
        Long deviceId = 2L;

        repo.save(deviceId, "TEMPERATURE", 10L, now.minus(60, ChronoUnit.MINUTES), "{\"temp\":19.0}");
        repo.save(deviceId, "TEMPERATURE", 10L, now.minus(30, ChronoUnit.MINUTES), "{\"temp\":21.0}");
        repo.save(deviceId, "TEMPERATURE", 10L, now.minus(10, ChronoUnit.MINUTES), "{\"temp\":23.0}");

        List<SensorReadingPoint> result = repo.findByDeviceIdBetween(
                deviceId,
                now.minus(2, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.MINUTES));

        assertEquals(3, result.size());
        assertTrue(result.get(0).timestamp().isAfter(result.get(1).timestamp()));
        assertTrue(result.get(1).timestamp().isAfter(result.get(2).timestamp()));
    }

    @Test
    void saveAndQuery_isolatedToRequestedDevice() {
        Instant now = Instant.now();

        repo.save(1L, "TEMPERATURE", 10L, now.minus(10, ChronoUnit.MINUTES), "{\"temp\":20.0}");
        repo.save(2L, "TEMPERATURE", 10L, now.minus(5,  ChronoUnit.MINUTES), "{\"temp\":25.0}");

        List<SensorReadingPoint> result = repo.findByDeviceIdBetween(
                1L,
                now.minus(1, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.MINUTES));

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).deviceId());
    }
}