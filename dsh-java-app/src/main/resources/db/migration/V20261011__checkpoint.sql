-- V20261011__checkpoint.sql
-- 检查点表：持久化 Session 执行过程中的检查点快照（LLM 请求前 / 工具副作用前）

CREATE TABLE IF NOT EXISTS checkpoint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    step_index BIGINT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    context_snapshot_json TEXT,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_checkpoint_session (session_id, step_index)
);
