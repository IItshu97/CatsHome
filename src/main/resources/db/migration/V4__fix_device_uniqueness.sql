-- Drop the old room-scoped uniqueness constraint
ALTER TABLE devices DROP CONSTRAINT IF EXISTS uq_device_type_room_name;

-- Add new global uniqueness constraint: device name must be unique per type across all rooms
-- (MQTT topic is {type}/{name} — no room_id segment)
ALTER TABLE devices ADD CONSTRAINT uq_device_type_name UNIQUE (device_type, name);