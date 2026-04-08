package com.catshome.smarthome.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sensor_readings")
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @PrePersist
    void onCreate() { if (timestamp == null) timestamp = Instant.now(); }

    public Long getId() { return id; }
    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}