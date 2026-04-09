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

Auto-generated on registration: `{deviceType.buildTopic(roomId, name)}` — lowercased, spaces → underscores, format `{type}/{roomId}/{name}`. The `buildTopic` method lives on `DeviceType` enum. Uniqueness is enforced by a `UNIQUE (device_type, room_id, name)` DB constraint and a pre-save check in `DeviceService`.

### MQTT integration

**Library:** Eclipse Paho v3 (`org.eclipse.paho.client.mqttv3:1.2.5`). Key classes in `com.catshome.smarthome.mqtt`:

- `MqttPublisher` — interface (outbound port): `publish(topic, payload)`
- `MqttClientWrapper` — `@Component`, `InitializingBean`: manages Paho client lifecycle, subscribes to wildcard patterns on startup, reconnects automatically. Fails silently if broker is unavailable on startup (logs error, does not crash). Wires into `DeviceService` for all outbound commands.
- `MqttMessageHandler` — `@Component`: routes inbound messages by topic segment count:
  - 3 segments → state update (`state_json`, `device_state_log`, InfluxDB for sensor types)
  - 4 segments `/status` → `devices.online` + `last_seen`
  - 4 segments `/state` → shutter position state
  - 4 segments `/raw` → smoke sensor raw ADC (merged into existing `state_json`)

**Subscriptions (QoS):**
```
+/+/+         QoS 1  — main state updates
+/+/+/status  QoS 1  — LWT online/offline
+/+/+/state   QoS 1  — shutter position
+/+/+/raw     QoS 0  — smoke raw ADC (informational)
```

**Outbound command topic mapping:**
- Light relay: `{topic}` → `"1"` / `"0"` (REST `"on"`/`"off"` translated)
- Light dimmer: `{topic}` → `"0"`–`"100"`
- Shutter command: `{topic}/command` → `"open"` / `"close"` / `"stop"`
- Shutter position: `{topic}/position` → integer string
- Thermostat: `{topic}/set` → `{"target": ..., "mode": "..."}`
- Energy reset: `{topic}/reset_energy` → `"reset"`

**Configuration (env vars with defaults):**
- `MQTT_BROKER_URL` — default `tcp://localhost:1883`
- `MQTT_CLIENT_ID` — default `catshome-server`
- `MQTT_USERNAME` — default empty
- `MQTT_PASSWORD` — default empty

Priority alarm devices (SMOKE, FLOOD): alarm state logs a `WARN` and is a placeholder for FCM (TODO).

### Persistence

- `sensor_readings` — **usunięte** (Flyway V2), zastąpione przez InfluxDB.
- `device_state_log` — old/new value log for state-change devices (LIGHT, DOOR, WINDOW, MOTION, SMOKE, FLOOD, SHUTTER).

### Error handling

`GlobalExceptionHandler` maps:
- `ResourceNotFoundException` → 404
- `DuplicateResourceException` → 409
- `InvalidOperationException` → 422 (wrong command for device type, MQTT broker not connected)
- `MethodArgumentNotValidException` → 400 with field-level errors

All use RFC 7807 `ProblemDetail` format.

## Tests

Three layers, all in `src/test/java/com/catshome/smarthome/`:

| Layer | Location | What it covers |
|---|---|---|
| **Unit** | `unit/` | `DeviceTypeTest`, `DeviceServiceTest`, `RoomServiceTest`, `DeviceHealthPollerTest` — pure JUnit 5 + Mockito, no Spring context |
| **Integration** | `integration/` | `DeviceRepositoryIT`, `RoomRepositoryIT`, `DeviceStateLogRepositoryIT` — `@DataJpaTest` + Testcontainers PostgreSQL; `InfluxSensorReadingRepositoryIT` — InfluxDB repo; `MqttMessageHandlerIT` — full MQTT flow with HiveMQ container |
| **System** | `system/` | `RoomControllerSystemTest`, `DeviceControllerSystemTest` — `@SpringBootTest` + MockMvc, full HTTP stack |

Integration and system tests share PostgreSQL + InfluxDB containers via `AbstractContainerTest` — singleton pattern using a static initializer block. `MqttMessageHandlerIT` additionally starts a HiveMQ CE container and adds `mqtt.broker-url` via its own `@DynamicPropertySource`. System tests have no broker — `MqttClientWrapper` logs a connect error and starts cleanly (graceful degradation).

> **Note:** Testcontainers is pinned to **1.21.4** in `pom.xml` (overriding Spring Boot BOM's 1.20.x) because Docker Engine 29+ dropped support for API versions below 1.40, and docker-java 3.4.x (bundled in 1.20.x) hardcodes API 1.32.

```bash
mvn test                                          # all tests
mvn test -Dtest="DeviceTypeTest,RoomServiceTest"  # unit only (no DB needed)
mvn test -Dtest="*IT"                             # integration tests only
mvn test -Dtest="*SystemTest"                     # system tests only
```

## InfluxDB

Time-series store for sensor readings (TEMPERATURE, THERMOSTAT, LUX, ENERGY — `DeviceType.hasReadings()`).

- Measurement: `sensor_reading`, tags: `device_id`, `device_type`, `room_id`, field: `payload` (JSON string)
- Write: `InfluxSensorReadingRepository.save()` — called by `MqttMessageHandler` on every inbound sensor message
- Read: `GET /devices/{id}/readings?from=...&to=...` → `List<SensorReadingPoint>`
- `SensorReadingStore` interface allows mocking in unit tests

Konfiguracja (env vars z domyślnymi):
- `INFLUXDB_URL` — domyślnie `http://localhost:8086`
- `INFLUXDB_TOKEN` — domyślnie `my-admin-token`
- `INFLUXDB_ORG` — domyślnie `catshome`
- `INFLUXDB_BUCKET` — domyślnie `smarthome`

## Docker

```bash
# Local dev (x86)
docker compose up --build

# Multi-arch build for Raspberry Pi (ARM64) — requires buildx
docker buildx build --platform linux/amd64,linux/arm64 -t catshome-smarthome:latest .
```

Konfiguracja przez env vars (z domyślnymi dla local dev):
- `DB_URL` — domyślnie `jdbc:postgresql://localhost:5432/smarthomedb`
- `DB_USERNAME` — domyślnie `smarthome`
- `DB_PASSWORD` — domyślnie `smarthome`

## Observability

Actuator endpoints exposed at `/actuator` (outside the `/api/v1` context-path):
- `GET /actuator/health` — app + DB health, details always shown
- `GET /actuator/metrics` — Micrometer metrics
- `GET /actuator/prometheus` — Prometheus scrape endpoint

Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`
OpenAPI JSON: `http://localhost:8080/api/v1/api-docs`

## Device Health Polling

`DeviceHealthPoller` (`@Scheduled`) polls every registered device every 60 s via `GET http://{ip}/health` (ESPHome built-in `web_server` endpoint).

On success: updates `firmware_version`, sets `online = true`, refreshes `last_seen`.
On failure: logs at DEBUG and leaves `online` unchanged — MQTT LWT remains the authoritative online/offline source.

- `DeviceHealthClient` — interface (`fetchHealth(ip)` → `Optional<DeviceHealthResponse>`)
- `RestClientDeviceHealthClient` — `RestClient` impl with 5 s connect + read timeouts
- `DeviceHealthPoller` — injects `DeviceRepository` + `DeviceHealthClient`; `@Transactional` per poll cycle

Configuration:
- `DEVICE_HEALTH_POLL_INTERVAL_MS` — default `60000`

## Planned / Not Yet Implemented

- SSE / WebSockets — real-time push to frontend after state changes received via MQTT.
- Device provisioning — HTTP POST to `http://{ip}/config/set` on register/update (sends broker IP + topic to device).
- Priority alarm push notifications (smoke, flood → FCM). MQTT handler logs `WARN` as placeholder.
- Authentication / JWT.
- Rate limiting.