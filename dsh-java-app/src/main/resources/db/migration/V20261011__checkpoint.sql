-- V20261011__checkpoint.sql
-- 检查点表：持久化 Session 执行过程中的检查点快照（LLM 请求前 / 工具副作用前）

CREATE TABLE IF NOT EXISTS checkpoint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    step_index BIGINT NOT NULL COMMENT '会话步次索引',
    reason VARCHAR(64) NOT NULL COMMENT '检查点原因（before-model-request / before-tool-side-effect）',
    context_snapshot_json TEXT NULL COMMENT '上下文快照 JSON',
    created_at TIMESTAMP NOT NULL COMMENT '创建时间',
    INDEX idx_checkpoint_session (session_id, step_index)
) COMMENT='检查点表：持久化 Session 执行过程中的检查点快照（LLM 请求前 / 工具副作用前）';