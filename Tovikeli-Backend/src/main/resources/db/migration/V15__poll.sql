CREATE TABLE poll (
    id TEXT PRIMARY KEY,
    content_id TEXT NOT NULL UNIQUE REFERENCES content(id) ON DELETE CASCADE,
    multi BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at BIGINT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE poll_option (
    id TEXT PRIMARY KEY,
    poll_id TEXT NOT NULL REFERENCES poll(id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    position INTEGER NOT NULL,
    UNIQUE (poll_id, position)
);

CREATE TABLE poll_vote (
    poll_id TEXT NOT NULL REFERENCES poll(id) ON DELETE CASCADE,
    option_id TEXT NOT NULL REFERENCES poll_option(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES platform_user(id) ON DELETE CASCADE,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (poll_id, user_id, option_id)
);

CREATE INDEX idx_poll_option_poll_position ON poll_option (poll_id, position);
CREATE INDEX idx_poll_vote_poll ON poll_vote (poll_id);
CREATE INDEX idx_poll_vote_user_poll ON poll_vote (user_id);
