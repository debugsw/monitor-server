CREATE TABLE ops_nginx_task (
    id VARCHAR(36) PRIMARY KEY,
    server_id VARCHAR(36) NOT NULL REFERENCES ops_server(id),
    instance_key VARCHAR(128) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    state VARCHAR(32) NOT NULL,
    expected_digest VARCHAR(64),
    config_content CLOB,
    result_message CLOB,
    result_content CLOB,
    result_digest VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_nginx_task_server_state ON ops_nginx_task(server_id, state, created_at);
CREATE INDEX idx_nginx_task_instance_time ON ops_nginx_task(server_id, instance_key, created_at);
CREATE TABLE ops_nginx_publish_record (
    task_id VARCHAR(36) PRIMARY KEY REFERENCES ops_nginx_task(id),
    server_id VARCHAR(36) NOT NULL REFERENCES ops_server(id),
    instance_key VARCHAR(128) NOT NULL,
    expected_digest VARCHAR(64) NOT NULL,
    result_digest VARCHAR(64),
    state VARCHAR(32) NOT NULL,
    message CLOB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_nginx_publish_instance ON ops_nginx_publish_record(server_id, instance_key, created_at);
