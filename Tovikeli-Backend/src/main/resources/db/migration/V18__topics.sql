-- V18: first-party discussion topics for local content.
CREATE TABLE topic (
    id              TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    normalized_name TEXT NOT NULL UNIQUE,
    description     TEXT,
    created_at      BIGINT NOT NULL,
    updated_at      BIGINT NOT NULL
);

CREATE TABLE topic_follow (
    topic_id  TEXT NOT NULL REFERENCES topic (id) ON DELETE RESTRICT,
    user_id   TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (topic_id, user_id)
);

CREATE INDEX idx_topic_follow_user_created
    ON topic_follow (user_id, created_at DESC, topic_id);

ALTER TABLE content
    ADD COLUMN topic_id TEXT REFERENCES topic (id) ON DELETE RESTRICT;

CREATE INDEX idx_content_topic_created
    ON content (topic_id, created_at DESC, id DESC);
