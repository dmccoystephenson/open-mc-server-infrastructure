-- Retrieval records (server status history)
CREATE TABLE retrieval_records (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    success BOOLEAN NOT NULL,
    player_count INT NOT NULL,
    tps VARCHAR(255),
    memory_used VARCHAR(255),
    memory_max VARCHAR(255),
    memory_free VARCHAR(255),
    memory_used_percent DOUBLE PRECISION NOT NULL DEFAULT 0.0
);

CREATE INDEX idx_retrieval_records_timestamp ON retrieval_records (timestamp DESC);

-- Deployment records (plugin deployment history)
CREATE TABLE deployment_records (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    plugin_name VARCHAR(255),
    status VARCHAR(255),
    source VARCHAR(255),
    branch VARCHAR(255),
    repo_url VARCHAR(1024),
    message TEXT
);

CREATE INDEX idx_deployment_records_timestamp ON deployment_records (timestamp DESC);

-- Activity tracker snapshots
CREATE TABLE activity_tracker_snapshots (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    success BOOLEAN NOT NULL,
    unique_logins INT,
    total_logins INT
);

CREATE INDEX idx_activity_tracker_snapshots_timestamp ON activity_tracker_snapshots (timestamp DESC);

-- Leaderboard entries (child of activity_tracker_snapshots)
CREATE TABLE leaderboard_entries (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES activity_tracker_snapshots(id) ON DELETE CASCADE,
    player_uuid VARCHAR(255),
    player_name VARCHAR(255),
    hours_played DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    total_logins INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_leaderboard_entries_snapshot_id ON leaderboard_entries (snapshot_id);
