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
2
All 11 device types (`LIGHT`, `DOOR`, `WINDOW`, `TEMPERATURE`, `MOTION`, `SMOKE`, `SHUTTER`, `THERMOSTAT`, `FLOOD`, `LUX`, `ENERGY`) live in a single `devices` table. `DeviceType` enum drives routing everywhere. The `is_dimmer` boolean distinguishes relay from PWM dimmer (both have `device_type = LIGHT`).

`state_json` (TEXT) stores the last known state as a device-type-specific JSON blob. `DeviceResponse` deserializes it to `Map<String, Object>` for the API response.

### MQTT topic generation

Auto-generated on registration: `{deviceType.buildTopic(roomId, name)}` — lowercased, spaces → underscores, format `{type}/{roomId}/{name}`. The `buildTopic` method lives on `DeviceType` enum. Uniqueness is enforced by a `UNIQUE (device_type, room_id, name)` DB constraint and a pre-save check in `DeviceService`.

### Persistence

- `sensor_readings` — **usunięte** (Flyway V2), zastąpione przez InfluxDB.
- `device_state_log` — old/new value log for state-change devices (LIGHT, DOOR, WINDOW, MOTION, SMOKE, FLOOD, SHUTTER).

### Error handling

`GlobalExceptionHandler` maps:
- `ResourceNotFoundException` → 404
- `DuplicateResourceException` → 409
- `InvalidOperationException` → 422 (wrong command for device type, MQTT not yet wired)
- `MethodArgumentNotValidException` → 400 with field-level errors

All use RFC 7807 `ProblemDetail` format.

## Tests

Three layers, all in `src/test/java/com/catshome/smarthome/`:

| Layer | Location | What it covers |
|---|---|---|
| **Unit** | `unit/` | `DeviceTypeTest`, `DeviceServiceTest`, `RoomServiceTest` — pure JUnit 5 + Mockito, no Spring context |
| **Integration** | `integration/` | `DeviceRepositoryIT`, `RoomRepositoryIT`, `DeviceStateLogRepositoryIT` — `@DataJpaTest` + Testcontainers PostgreSQL; `InfluxSensorReadingRepositoryIT` — bezpośrednie testy repozytorium InfluxDB |
| **System** | `system/` | `RoomControllerSystemTest`, `DeviceControllerSystemTest` — `@SpringBootTest` + MockMvc, full HTTP stack |

Integration and system tests share a single PostgreSQL container via `AbstractContainerTest` — singleton pattern using a static initializer block (`POSTGRES.start()` in `static {}`). The container starts once per JVM; Testcontainers registers a shutdown hook to clean it up. `@DynamicPropertySource` overrides `spring.datasource.*` so the test context always uses the container's dynamic port regardless of `application.yml`.

> **Note:** Testcontainers is pinned to **1.21.4** in `pom.xml` (overriding Spring Boot BOM's 1.20.x) because Docker Engine 29+ dropped support for API versions below 1.40, and docker-java 3.4.x (bundled in 1.20.x) hardcodes API 1.32.

```bash
mvn test                                          # all tests
mvn test -Dtest="DeviceTypeTest,RoomServiceTest"  # unit only (no DB needed)
mvn test -Dtest="*IT"                             # integration tests only
mvn test -Dtest="*SystemTest"                     # system tests only
```

## InfluxDB

Time-series store dla odczytów sensorów (TEMPERATURE, THERMOSTAT, LUX, ENERGY — `DeviceType.hasReadings()`).

- Measurement: `sensor_reading`, tagi: `device_id`, `device_type`, `room_id`, pole: `payload` (JSON string)
- Zapis: `InfluxSensorReadingRepository.save()` — wywoływany przez przyszłą integrację MQTT
- Odczyt: `GET /devices/{id}/readings?from=...&to=...` → `List<SensorReadingPoint>`
- Interfejs `SensorReadingStore` pozwala mockować repozytorium w testach jednostkowych

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

## Planned / Not Yet Implemented

- MQTT broker integration — `DeviceService` command methods throw 422 with "MQTT not yet integrated" until this is added. Subscribe patterns: `+/+/+` (state), `+/+/+/status` (LWT), `+/+/+/state` (shutter), `+/+/+/raw` (smoke).
- SSE / WebSockets — real-time push to Angular frontend (after MQTT integration).
- Device provisioning — HTTP POST to `http://{ip}/config/set` on register/update.
- Device health polling — `GET http://{ip}/health` every 60 s.
- Priority alarm push notifications (smoke, flood → FCM).
- Dockerfile + docker-compose with ARM64 support (target: Raspberry Pi).
- Authentication / JWT.
- Rate limiting.