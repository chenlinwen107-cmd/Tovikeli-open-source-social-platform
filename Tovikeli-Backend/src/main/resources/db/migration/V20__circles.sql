CREATE TABLE circle (
    id BIGSERIAL PRIMARY KEY,
    slug TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL DEFAULT '兴趣',
    icon TEXT NOT NULL DEFAULT 'compass',
    color TEXT NOT NULL DEFAULT '#2b54f0',
    owner_id TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT,
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_circle_hot ON circle (created_at DESC, id DESC);

CREATE TABLE circle_member (
    circle_id BIGINT NOT NULL REFERENCES circle (id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (circle_id, user_id)
);

CREATE INDEX idx_circle_member_user ON circle_member (user_id, circle_id);

CREATE TABLE circle_post (
    circle_id BIGINT NOT NULL REFERENCES circle (id) ON DELETE CASCADE,
    content_id TEXT NOT NULL UNIQUE REFERENCES content (id) ON DELETE RESTRICT,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (circle_id, content_id)
);

CREATE INDEX idx_circle_post_latest ON circle_post (circle_id, created_at DESC, content_id DESC);

CREATE TABLE circle_chat_message (
    id BIGSERIAL PRIMARY KEY,
    circle_id BIGINT NOT NULL REFERENCES circle (id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT,
    content TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_circle_chat_latest ON circle_chat_message (circle_id, id DESC);
