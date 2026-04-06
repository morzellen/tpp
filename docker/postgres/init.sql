-- init.sql
-- Инициализация базы данных при первом запуске PostgreSQL
-- Этот скрипт выполняется автоматически через docker-entrypoint-initdb.d

-- Создание таблиц из V1__initial_schema.sql
CREATE TABLE IF NOT EXISTS events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL,
    aggregate_type  TEXT NOT NULL,
    event_type      TEXT NOT NULL,
    event_data      JSONB NOT NULL,
    version         INT  NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_aggregate_version
        UNIQUE (aggregate_id, version)
);

CREATE INDEX IF NOT EXISTS idx_events_aggregate ON events(aggregate_id);

CREATE TABLE IF NOT EXISTS transactions (
    id              UUID PRIMARY KEY,
    from_account_id UUID NOT NULL,
    to_account_id   UUID NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    currency        CHAR(3) NOT NULL,
    status          TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transactions_from_account ON transactions(from_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_to_account ON transactions(to_account_id);

CREATE TABLE IF NOT EXISTS accounts (
    id          UUID PRIMARY KEY,
    owner_id    TEXT NOT NULL,
    balance     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency    CHAR(3) NOT NULL DEFAULT 'RUB',
    status      TEXT NOT NULL DEFAULT 'Active',
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Seed Data
INSERT INTO accounts (id, owner_id, balance, currency, status)
VALUES
    ('550e8400-e29b-41d4-a716-446655440000', 'owner-1', 10000.0000, 'RUB', 'Active'),
    ('6ba7b810-9dad-11d1-80b4-00c04fd430c8', 'owner-2', 5000.0000, 'RUB', 'Active')
ON CONFLICT (id) DO NOTHING;
