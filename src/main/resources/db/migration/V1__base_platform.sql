CREATE TABLE ops_server (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    environment VARCHAR(64) NOT NULL,
    tags VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE ops_agent (
    id VARCHAR(36) PRIMARY KEY,
    server_id VARCHAR(36) NOT NULL UNIQUE REFERENCES ops_server(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    version VARCHAR(100),
    last_heartbeat_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE ops_instance (
    id VARCHAR(36) PRIMARY KEY,
    server_id VARCHAR(36) NOT NULL REFERENCES ops_server(id),
    name VARCHAR(100) NOT NULL,
    type VARCHAR(64) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_instance_server_type_name UNIQUE (server_id, type, name)
);
CREATE INDEX idx_instance_server ON ops_instance(server_id);
CREATE TABLE ops_host_metric (
    id VARCHAR(36) PRIMARY KEY,
    server_id VARCHAR(36) NOT NULL REFERENCES ops_server(id),
    collected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    cpu_percent DOUBLE PRECISION NOT NULL,
    memory_total_bytes BIGINT NOT NULL,
    memory_used_bytes BIGINT NOT NULL,
    disk_total_bytes BIGINT NOT NULL,
    disk_used_bytes BIGINT NOT NULL,
    load_one DOUBLE PRECISION NOT NULL,
    network_rx_bytes BIGINT NOT NULL,
    network_tx_bytes BIGINT NOT NULL
);
CREATE INDEX idx_host_metric_server_time ON ops_host_metric(server_id, collected_at);
