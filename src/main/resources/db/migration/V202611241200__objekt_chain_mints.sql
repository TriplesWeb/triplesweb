CREATE TABLE objekt_mint_cursor (
    chain_id VARCHAR(40) NOT NULL,
    contract VARCHAR(42) NOT NULL,
    next_block BIGINT NOT NULL,
    PRIMARY KEY (chain_id, contract)
);

CREATE TABLE objekt_mint_total (
    slug VARCHAR(200) NOT NULL PRIMARY KEY,
    baseline_count BIGINT NOT NULL,
    new_mints BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE objekt_mint_event (
    chain_id VARCHAR(40) NOT NULL,
    contract VARCHAR(42) NOT NULL,
    token_id VARCHAR(78) NOT NULL,
    event_kind VARCHAR(8) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    block_number BIGINT NOT NULL,
    log_index INT NOT NULL,
    tx_hash VARCHAR(66) NOT NULL,
    PRIMARY KEY (chain_id, contract, block_number, log_index),
    UNIQUE KEY uk_objekt_mint_token_kind (chain_id, contract, token_id, event_kind),
    INDEX idx_objekt_mint_event_slug (slug, event_kind)
);
