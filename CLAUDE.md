# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.4.4 / Java 21 REST server for a smart home system ("CatsHome"). Engineering thesis project — a rewrite of an original Node.js server. The mobile app (React Native) and IoT devices (ESP8266/ESPHome) are separate components not in this repo.

The authoritative specification for all device types, MQTT topics, and API contracts is at `/home/domkot/Pobrane/Praca_Inzynierska/device_contract.md`.

## Commands

```bash
# Build
mvn compile

# Run (requires PostgreSQL running)
mvn spring-boot:run

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=DeviceServiceTest

# Package
mvn package -DskipTests
```

## Prerequisites

- PostgreSQL running locally. Create DB and user:
  ```sql
  CREATE DATABASE smarthomedb;
  CREATE USER smarthome WITH PASSWORD 'smarthome';
  GRANT ALL PRIVILEGES ON DATABASE smarthomedb TO smarthome;
  ```
- DB credentials default to `smarthome`/`smarthome`; override via env vars `DB_USERNAME` / `DB_PASSWORD`.
- Schema is managed by Flyway — migrations run automatically on startup from `src/main/resources/db/migration/`.

## Architecture

**Layered: Controller → Service → Repository → Entity**

All endpoints are under `/api/v1` (the servlet context-path in `application.yml`):
- `GET/POST /api/v1/rooms`, `GET/PUT/DELETE /api/v1/rooms/{id}`
- `GET/POST /api/v1/devices`, `GET/PUT/DELETE /api/v1/devices/{id}`
- `POST /api/v1/devices/{id}/command|brightness|position|thermostat|reset_energy`
- `GET /api/v1/devices/{id}/history|readings`
- `GET /api/v1/devices/alarms`

### Unified device model

All 11 device types (`LIGHT`, `DOOR`, `WINDOW`, `TEMPERATURE`, `MOTION`, `SMOKE`, `SHUTTER`, `THERMOSTAT`, `FLOOD`, `LUX`, `ENERGY`) live in a single `devices` table. `DeviceType` enum drives routing everywhere. The `is_dimmer` boolean distinguishes relay from PWM dimmer (both have `device_type = LIGHT`).

`state_json` (TEXT) stores the last known state as a device-type-specific JSON blob. `DeviceResponse` deserializes it to `Map<String, Object>` for the API response.

### MQTT topic generation

Auto-generated on registration: `{deviceType.topicPrefix()}/{room.id}/{normalizedName}` where name is lowercased with spaces → underscores. Uniqueness is enforced by a `UNIQUE (device_type, room_id, name)` constraint and a pre-save check in `DeviceService`.

### Persistence

- `sensor_readings` — time-series payload blobs for TEMPERATURE, THERMOSTAT, LUX, ENERGY (checked via `DeviceType.hasReadings()`).
- `device_state_log` — old/new value log for state-change devices (LIGHT, DOOR, WINDOW, MOTION, SMOKE, FLOOD, SHUTTER).

### Error handling

`GlobalExceptionHandler` maps:
- `ResourceNotFoundException` → 404
- `DuplicateResourceException` → 409
- `InvalidOperationException` → 422 (wrong command for device type, MQTT not yet wired)
- `MethodArgumentNotValidException` → 400 with field-level errors

All use RFC 7807 `ProblemDetail` format.

## Planned / Not Yet Implemented

- MQTT broker integration — `DeviceService` command methods throw 422 with "MQTT not yet integrated" until this is added. Subscribe patterns: `+/+/+` (state), `+/+/+/status` (LWT), `+/+/+/state` (shutter), `+/+/+/raw` (smoke).
- Device provisioning — HTTP POST to `http://{ip}/config/set` on register/update.
- Device health polling — `GET http://{ip}/health` every 60 s.
- Priority alarm push notifications (smoke, flood → FCM).
- Authentication / JWT.
- Rate limiting.