-- V20261010__token_snapshot.sql
-- Token 快照表：持久化 ReplayAwareTokenMeter 的增量合流检查点

CREATE TABLE IF NOT EXISTS token_snapshot (
    session_id VARCHAR(64) PRIMARY KEY,
    last_event_step_index BIGINT NOT NULL,
    input_tokens INT NOT NULL,
    output_tokens INT NOT NULL,
    cache_read_tokens INT NULL,
    cache_write_tokens INT NULL,
    reasoning_tokens INT NULL,
    model_calls BIGINT NOT NULL
);
