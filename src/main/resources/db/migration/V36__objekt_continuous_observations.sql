-- Public index observations, separate from authenticated account snapshots.
CREATE TABLE IF NOT EXISTS objekt_ingest_cursor (
    lane VARCHAR(16) NOT NULL PRIMARY KEY,
    watermark_ms BIGINT NOT NULL,
    cycle_head_ms BIGINT NULL,
    lower_bound_ms BIGINT NULL,
    cursor_time VARCHAR(40) NULL,
    cursor_id VARCHAR(200) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    complete BOOLEAN NOT NULL DEFAULT FALSE,
    last_success_ms BIGINT NULL
);

CREATE TABLE IF NOT EXISTS objekt_observed_transfer (
    source_id VARCHAR(200) NOT NULL PRIMARY KEY,
    token_id VARCHAR(78) NOT NULL,
    from_address CHAR(42) NOT NULL,
    to_address CHAR(42) NOT NULL,
    tx_hash CHAR(66) NOT NULL,
    transferred_at_ms BIGINT NOT NULL,
    observed_at_ms BIGINT NOT NULL,
    KEY idx_observed_transfer_token (token_id, transferred_at_ms),
    KEY idx_observed_transfer_from (from_address, transferred_at_ms),
    KEY idx_observed_transfer_to (to_address, transferred_at_ms)
);

CREATE TABLE IF NOT EXISTS objekt_observed_token (
    token_id VARCHAR(78) NOT NULL PRIMARY KEY,
    collection_slug VARCHAR(200) NULL,
    collection_id VARCHAR(200) NULL,
    artist VARCHAR(40) NULL,
    reported_serial BIGINT NULL,
    serial_observed_at_ms BIGINT NULL,
    confirmed_serial BIGINT NULL,
    confirmed_collection_id VARCHAR(200) NULL,
    confirmed_at_ms BIGINT NULL,
    indexed_owner CHAR(42) NULL,
    last_transfer_ms BIGINT NULL,
    last_transfer_id VARCHAR(200) NULL,
    KEY idx_observed_token_collection (collection_slug),
    KEY idx_observed_token_owner (indexed_owner)
);
