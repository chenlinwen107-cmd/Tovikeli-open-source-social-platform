CREATE TABLE user_follow (
    follower_user_id TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT,
    followed_user_id TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (follower_user_id, followed_user_id),
    CONSTRAINT ck_user_follow_no_self CHECK (follower_user_id <> followed_user_id)
);

CREATE INDEX idx_user_follow_follower_created
    ON user_follow (follower_user_id, created_at DESC, followed_user_id);

CREATE INDEX idx_user_follow_followed_created
    ON user_follow (followed_user_id, created_at DESC, follower_user_id);

ALTER TABLE media ADD COLUMN kind TEXT NOT NULL DEFAULT 'OTHER';
CREATE INDEX idx_media_kind ON media (kind);
