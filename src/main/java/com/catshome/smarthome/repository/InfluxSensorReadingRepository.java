package com.catshome.smarthome.repository;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.catshome.smarthome.dto.SensorReadingPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class InfluxSensorReadingRepository implements SensorReadingStore {

    static final String MEASUREMENT = "sensor_reading";

    private final InfluxDBClient client;
    private final String org;
    private final String bucket;

    public InfluxSensorReadingRepository(InfluxDBClient client,
                                          @Value("${influxdb.org}") String org,
                                          @Value("${influxdb.bucket}") String bucket) {
        this.client = client;
        this.org = org;
        this.bucket = bucket;
    }

    public void save(Long deviceId, String deviceType, Long roomId, Instant timestamp, String payload) {
        WriteApiBlocking writeApi = client.getWriteApiBlocking();
        Point point = Point.measurement(MEASUREMENT)
                .addTag("device_id", String.valueOf(deviceId))
                .addTag("device_type", deviceType)
                .addTag("room_id", String.valueOf(roomId))
                .addField("payload", payload)
                .time(timestamp, WritePrecision.MS);
        writeApi.writePoint(bucket, org, point);
    }

    public List<SensorReadingPoint> findByDeviceIdBetween(Long deviceId, Instant from, Instant to) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r._measurement == "%s")
                  |> filter(fn: (r) => r.device_id == "%s")
                  |> sort(columns: ["_time"], desc: true)
                """.formatted(bucket, from.toString(), to.toString(), MEASUREMENT, deviceId);

        QueryApi queryApi = client.getQueryApi();
        return queryApi.query(flux, org).stream()
                .flatMap(table -> table.getRecords().stream())
                .map(record -> new SensorReadingPoint(deviceId, record.getTime(), (String) record.getValue()))
                .toList();
    }
}
