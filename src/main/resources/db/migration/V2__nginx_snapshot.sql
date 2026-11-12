CREATE TABLE ops_nginx_snapshot (
    server_id VARCHAR(36) PRIMARY KEY REFERENCES ops_server(id),
    collected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    partial BOOLEAN NOT NULL,
    instances_json CLOB NOT NULL
);
