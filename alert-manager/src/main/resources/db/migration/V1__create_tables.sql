-- Alert records
CREATE TABLE alert_records (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255),
    message TEXT,
    level VARCHAR(50) NOT NULL,
    source VARCHAR(255),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_alert_records_received_at ON alert_records (received_at DESC);
