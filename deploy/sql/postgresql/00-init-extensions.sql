-- PostgreSQL Extensions for AI Agent Platform
-- Run this first before schema creation

CREATE EXTENSION IF NOT EXISTS vector;      -- PgVector for embedding storage
CREATE EXTENSION IF NOT EXISTS pg_trgm;     -- Trigram text search
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; -- UUID generation
