CREATE TABLE content_comment (
    id                TEXT PRIMARY KEY,
    user_id           TEXT NOT NULL
        REFERENCES platform_user (id) ON DELETE RESTRICT,
    content_id        TEXT NOT NULL
        REFERENCES content (id) ON DELETE RESTRICT,
    parent_comment_id TEXT
        REFERENCES content_comment (id) ON DELETE RESTRICT,
    text              TEXT NOT NULL,
    status            TEXT NOT NULL,
    created_at        BIGINT NOT NULL,
    updated_at        BIGINT NOT NULL
);

CREATE INDEX idx_content_comment_content_created
    ON content_comment (content_id, created_at ASC, id ASC);

CREATE INDEX idx_content_comment_parent_created
    ON content_comment (parent_comment_id, created_at ASC, id ASC);

CREATE INDEX idx_content_comment_user_created
    ON content_comment (user_id, created_at DESC);
