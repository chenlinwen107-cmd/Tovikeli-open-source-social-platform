CREATE TABLE browse_history (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES platform_user(id) ON DELETE CASCADE,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT '',
    type_label TEXT NOT NULL DEFAULT '',
    link TEXT NOT NULL DEFAULT '',
    icon TEXT NOT NULL DEFAULT 'clock',
    author_nickname TEXT NOT NULL DEFAULT '',
    cover TEXT NOT NULL DEFAULT '',
    solved BOOLEAN NOT NULL DEFAULT FALSE,
    viewed_at BIGINT NOT NULL,
    UNIQUE (user_id, target_type, target_id)
);

CREATE INDEX idx_browse_history_user_viewed
ON browse_history (user_id, viewed_at DESC, id DESC);
