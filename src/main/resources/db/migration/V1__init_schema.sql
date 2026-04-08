CREATE TABLE rooms (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE lights (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    address    VARCHAR(255) NOT NULL UNIQUE,
    topic      VARCHAR(255) NOT NULL UNIQUE,
    state      VARCHAR(10)  NOT NULL DEFAULT 'off',
    room_id    BIGINT       NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE door_sensors (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    address    VARCHAR(255) NOT NULL UNIQUE,
    topic      VARCHAR(255) NOT NULL UNIQUE,
    state      VARCHAR(10)  NOT NULL DEFAULT 'closed',
    room_id    BIGINT       NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE window_sensors (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    address    VARCHAR(255) NOT NULL UNIQUE,
    topic      VARCHAR(255) NOT NULL UNIQUE,
    state      VARCHAR(10)  NOT NULL DEFAULT 'closed',
    room_id    BIGINT       NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE temperature_sensors (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255)     NOT NULL UNIQUE,
    address     VARCHAR(255)     NOT NULL UNIQUE,
    topic       VARCHAR(255)     NOT NULL UNIQUE,
    temperature DOUBLE PRECISION,
    humidity    DOUBLE PRECISION,
    room_id     BIGINT           NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_lights_room_id             ON lights(room_id);
CREATE INDEX idx_door_sensors_room_id       ON door_sensors(room_id);
CREATE INDEX idx_window_sensors_room_id     ON window_sensors(room_id);
CREATE INDEX idx_temperature_sensors_room_id ON temperature_sensors(room_id);
