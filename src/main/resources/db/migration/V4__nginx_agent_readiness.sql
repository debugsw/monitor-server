ALTER TABLE ops_agent ADD COLUMN nginx_tasks_ready BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE ops_agent ADD COLUMN nginx_tasks_message VARCHAR(500);
