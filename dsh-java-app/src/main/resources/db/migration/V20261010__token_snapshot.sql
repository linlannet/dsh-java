-- V20261010__token_snapshot.sql
-- Token 快照表：持久化 ReplayAwareTokenMeter 的增量合流检查点

CREATE TABLE IF NOT EXISTS token_snapshot (
    session_id VARCHAR(64) PRIMARY KEY COMMENT '会话ID（主键）',
    last_event_step_index BIGINT NOT NULL COMMENT '最后一次累计的事件步次索引',
    input_tokens INT NOT NULL COMMENT '累计输入 Token 数',
    output_tokens INT NOT NULL COMMENT '累计输出 Token 数',
    cache_read_tokens INT NULL COMMENT '缓存读取 Token 数',
    cache_write_tokens INT NULL COMMENT '缓存写入 Token 数',
    reasoning_tokens INT NULL COMMENT '推理 Token 数',
    model_calls BIGINT NOT NULL COMMENT '模型调用次数'
) COMMENT='Token 快照表：持久化 ReplayAwareTokenMeter 的增量合流检查点';