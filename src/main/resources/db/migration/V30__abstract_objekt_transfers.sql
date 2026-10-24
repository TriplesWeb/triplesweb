CREATE TABLE IF NOT EXISTS abstract_index_state (
    id TINYINT NOT NULL PRIMARY KEY,
    next_block BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO abstract_index_state (id, next_block)
SELECT 1, 6800000
WHERE NOT EXISTS (SELECT 1 FROM abstract_index_state WHERE id = 1);

CREATE TABLE IF NOT EXISTS abstract_objekt_transfer (
    block_number BIGINT NOT NULL,
    log_index INT NOT NULL,
    tx_hash CHAR(66) NOT NULL,
    token_id BIGINT NOT NULL,
    from_address CHAR(42) NOT NULL,
    to_address CHAR(42) NOT NULL,
    block_timestamp BIGINT NULL,
    PRIMARY KEY (block_number, log_index),
    KEY idx_abstract_transfer_token (token_id),
    KEY idx_abstract_transfer_to (to_address)
);

CREATE TABLE IF NOT EXISTS abstract_objekt_owner (
    token_id BIGINT NOT NULL PRIMARY KEY,
    owner_address CHAR(42) NOT NULL,
    block_number BIGINT NOT NULL,
    log_index INT NOT NULL,
    order_key BIGINT NOT NULL,
    KEY idx_abstract_owner_address (owner_address, token_id)
);

CREATE TABLE IF NOT EXISTS abstract_objekt_meta (
    token_id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(200) NULL,
    image VARCHAR(500) NULL,
    member VARCHAR(80) NULL,
    season VARCHAR(80) NULL,
    objekt_class VARCHAR(80) NULL,
    artist VARCHAR(80) NULL
);
