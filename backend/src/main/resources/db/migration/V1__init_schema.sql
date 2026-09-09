
-- ==========================================================
-- SCHEMA
-- ==========================================================

CREATE SCHEMA IF NOT EXISTS tradironi_schema;

-- Set search path
SET search_path TO tradironi_schema;

-- ==========================================================
-- TABLES
-- ==========================================================

-- user table
CREATE TABLE IF NOT EXISTS tradironi_user (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    surname VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(100) NOT NULL,
    role VARCHAR(100) NOT NULL,
    -- additional user info
    notes VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);