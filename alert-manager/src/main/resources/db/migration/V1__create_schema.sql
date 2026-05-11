CREATE TABLE alert_record (
    id BIGSERIAL PRIMARY KEY,
    title TEXT,
    message TEXT,
    level TEXT,
    source TEXT,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ar_received_at ON alert_record (received_at DESC);
