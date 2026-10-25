-- Authenticated account snapshots, separate from the complete public catalog.
-- Credentials and wallet addresses are never stored here.
CREATE TABLE IF NOT EXISTS cosmo_owned_objekt (
    scope_key VARCHAR(64) NOT NULL,
    token_id VARCHAR(78) NOT NULL,
    collection_id VARCHAR(120) NOT NULL,
    serial_number BIGINT NULL,
    acquired_at VARCHAR(40) NOT NULL,
    inventory_status VARCHAR(40) NOT NULL,
    collection_json LONGTEXT NOT NULL,
    PRIMARY KEY (scope_key, token_id)
);
CREATE TABLE IF NOT EXISTS cosmo_objekt_sync_state (
    scope_key VARCHAR(64) NOT NULL PRIMARY KEY,
    artist VARCHAR(30) NOT NULL,
    collection_count INT NOT NULL,
    objekt_count INT NOT NULL,
    synced_at BIGINT NOT NULL
);
