-- Run while the old and new applications are stopped.
-- Uses the original table and sequence names; preserves existing hotel rows.
BEGIN;
CREATE SEQUENCE IF NOT EXISTS hibernate_sequence START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS hotel (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    city VARCHAR(255),
    rating INTEGER
);
LOCK TABLE hotel IN ACCESS EXCLUSIVE MODE;
SELECT setval('hibernate_sequence',
    GREATEST((SELECT last_value FROM hibernate_sequence), COALESCE((SELECT MAX(id) FROM hotel), 1)),
    (SELECT is_called FROM hibernate_sequence) OR EXISTS(SELECT 1 FROM hotel));
COMMIT;
