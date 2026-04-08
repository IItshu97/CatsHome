CREATE TABLE rooms (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE devices (
    id               BIGSERIAL    PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    device_type      VARCHAR(20)  NOT NULL,
    is_dimmer        BOOLEAN      NOT NULL DEFAULT false,
    room_id          BIGINT       NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    ip_address       VARCHAR(45)  NOT NULL UNIQUE,
    mqtt_topic       VARCHAR(255) NOT NULL UNIQUE,
    online           BOOLEAN      NOT NULL DEFAULT false,
    last_seen        TIMESTAMPTZ,
    state_json       TEXT,
    firmware_version VARCHAR(20),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_device_type_room_name UNIQUE (device_type, room_id, name)
);

-- Time-series readings: temperature, thermostat, lux, energy
CREATE TABLE sensor_readings (
    id        BIGSERIAL   PRIMARY KEY,
    device_id BIGINT      NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload   TEXT        NOT NULL
);

-- State-change log: light, door, window, motion, smoke, flood, shutter
CREATE TABLE device_state_log (
    id        BIGSERIAL    PRIMARY KEY,
    device_id BIGINT       NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    timestamp TIMESTAMPTZ  NOT NULL DEFAULT now(),
    old_value VARCHAR(255),
    new_value VARCHAR(255) NOT NULL
);

CREATE INDEX idx_devices_room_id          ON devices(room_id);
CREATE INDEX idx_devices_device_type      ON devices(device_type);
CREATE INDEX idx_sensor_readings_device_ts ON sensor_readings(device_id, timestamp DESC);
CREATE INDEX idx_state_log_device_ts      ON device_state_log(device_id, timestamp DESC);
