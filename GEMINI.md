# GEMINI.md - CatsHome Smart Home Server

This project is a Spring Boot 3.4.4 / Java 21 rewrite of an original Node.js smart home automation server. It serves as the central backend for a smart home ecosystem comprising a React Native mobile app and various IoT devices (ESP8266/ESPHome).

## Project Overview

- **Core Technologies:** Java 21, Spring Boot 3.4.4, PostgreSQL, Spring Data JPA, Hibernate, Flyway.
- **Architecture:** Classic layered architecture: `Controller` → `Service` → `Repository` → `Entity`.
- **Primary Domain:** Smart home device management, state tracking, and sensor data collection.
- **Key Concepts:**
    - **Unified Device Model:** All 11 device types (LIGHT, SHUTTER, THERMOSTAT, etc.) are stored in a single `devices` table.
    - **Device State:** JSON-based state storage in `state_json` column.
    - **MQTT Topic Generation:** Managed automatically following the pattern `{type}/{roomId}/{normalizedName}`.
    - **Time-Series Data:** Sensor readings are stored in `sensor_readings`, while state changes are logged in `device_state_log`.

## Building and Running

### Prerequisites
- **Java 21** or higher.
- **Maven** for build management.
- **PostgreSQL** (local instance or Docker).
  - Default DB: `smarthomedb`, User: `smarthome`, Password: `smarthome`.

### Key Commands
- **Compile:** `mvn compile`
- **Run Application:** `mvn spring-boot:run`
- **Run All Tests:** `mvn test`
- **Package (Skip Tests):** `mvn package -DskipTests`

## Development Conventions

### API Standards
- **Base Path:** All API endpoints are prefixed with `/api/v1` (configured in `application.yml`).
- **Error Handling:** Follows RFC 7807 `ProblemDetail` format via `GlobalExceptionHandler`.
- **Validation:** Standard Jakarta Validation constraints are used in DTOs.

### Coding Patterns
- **Service Layer:** Business logic resides strictly in services (e.g., `DeviceService`).
- **DTOs:** Use Java Records for requests and responses (e.g., `DeviceRegistrationRequest`, `DeviceResponse`).
- **Enums:** `DeviceType` is a central enum that dictates device behavior, topic prefixes, and capabilities.
- **Database Migrations:** Managed by Flyway. New schema changes must be added as SQL scripts in `src/main/resources/db/migration/`.

### Testing Strategy
- **Unit Tests:** Located in `src/test/java/.../unit/` for service-level logic.
- **Integration Tests:** Located in `src/test/java/.../integration/`. These use **Testcontainers** to spin up a real PostgreSQL instance.
- **System Tests:** Located in `src/test/java/.../system/` for end-to-end API verification.

## Current Limitations & TODOs
- **MQTT Integration:** The command infrastructure is in place (e.g., `sendCommand`), but the actual MQTT broker integration is pending.
- **Authentication:** JWT-based security is planned but not yet implemented.
- **Device Health:** Polling and provisioning logic are future roadmap items.
