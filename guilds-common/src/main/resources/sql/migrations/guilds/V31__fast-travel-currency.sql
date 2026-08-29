CREATE TABLE player_travel_wallets (
    player_uuid TEXT PRIMARY KEY,
    balance BIGINT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE travel_currency_awards (
    source TEXT NOT NULL,
    event_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    amount BIGINT NOT NULL,
    awarded_at TEXT NOT NULL,
    PRIMARY KEY (source, event_id)
);

CREATE TABLE travel_currency_reservations (
    reservation_id TEXT PRIMARY KEY,
    trip_id TEXT NOT NULL UNIQUE,
    player_uuid TEXT NOT NULL,
    amount BIGINT NOT NULL,
    status TEXT NOT NULL,
    expires_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    completed_at BIGINT,
    released_at BIGINT
);

-- +index idx_travel_currency_reservations_status_expiry travel_currency_reservations (status, expires_at)
