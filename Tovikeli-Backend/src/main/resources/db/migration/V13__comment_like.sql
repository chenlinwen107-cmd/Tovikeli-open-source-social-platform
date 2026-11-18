CREATE TABLE comment_like (
    id         TEXT PRIMARY KEY,
    user_id    TEXT NOT NULL
        REFERENCES platform_user (id) ON DELETE RESTRICT,
    comment_id TEXT NOT NULL
        REFERENCES content_comment (id) ON DELETE RESTRICT,
    created_at BIGINT NOT NULL,
    CONSTRAINT uq_comment_like_user_comment UNIQUE (user_id, comment_id)
);

CREATE INDEX idx_comment_like_comment_created
    ON comment_like (comment_id, created_at DESC);

CREATE INDEX idx_comment_like_user_created
    ON comment_like (user_id, created_at DESC);
