CREATE TABLE activity_tracker_snapshot (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    success BOOLEAN NOT NULL,
    unique_logins INTEGER,
    total_logins INTEGER,
    leaderboard TEXT
);

CREATE INDEX idx_ats_timestamp ON activity_tracker_snapshot (timestamp);

CREATE TABLE deployment_record (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    plugin_name TEXT,
    status TEXT,
    source TEXT,
    branch TEXT,
    repo_url TEXT,
    message TEXT
);

CREATE INDEX idx_dr_timestamp ON deployment_record (timestamp);

CREATE TABLE retrieval_record (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    success BOOLEAN NOT NULL,
    player_count INTEGER NOT NULL,
    tps TEXT,
    memory_used TEXT,
    memory_max TEXT,
    memory_free TEXT,
    memory_used_percent DOUBLE PRECISION
);

CREATE INDEX idx_rr_timestamp ON retrieval_record (timestamp);
