CREATE TABLE forum_board (
    id TEXT PRIMARY KEY,
    slug TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    icon TEXT NOT NULL DEFAULT '💬',
    announcement TEXT,
    parent_id TEXT REFERENCES forum_board (id) ON DELETE RESTRICT,
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_forum_board_parent ON forum_board (parent_id, id);

CREATE TABLE forum_board_follow (
    board_id TEXT NOT NULL REFERENCES forum_board (id) ON DELETE RESTRICT,
    user_id TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (board_id, user_id)
);

CREATE TABLE forum_thread (
    id TEXT PRIMARY KEY,
    board_id TEXT NOT NULL REFERENCES forum_board (id) ON DELETE RESTRICT,
    content_id TEXT NOT NULL UNIQUE REFERENCES content (id) ON DELETE RESTRICT,
    title TEXT NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    elite BOOLEAN NOT NULL DEFAULT FALSE,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    last_reply_at BIGINT NOT NULL
);

CREATE INDEX idx_forum_thread_board_latest
    ON forum_thread (board_id, pinned DESC, last_reply_at DESC, id DESC);

CREATE INDEX idx_forum_thread_latest
    ON forum_thread (last_reply_at DESC, id DESC);

CREATE TABLE forum_thread_subscribe (
    thread_id TEXT NOT NULL REFERENCES forum_thread (id) ON DELETE RESTRICT,
    user_id TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (thread_id, user_id)
);

INSERT INTO forum_board (id, slug, name, description, icon, created_at) VALUES
('1', 'general', '综合讨论', '什么都可以聊，欢迎分享你的想法。', '💬', EXTRACT(EPOCH FROM NOW()) * 1000),
('2', 'anime', '动漫游戏', '动漫、漫画、游戏与二次元话题。', '🎮', EXTRACT(EPOCH FROM NOW()) * 1000),
('3', 'help', '问题求助', '遇到问题？来这里一起讨论解决。', '❓', EXTRACT(EPOCH FROM NOW()) * 1000)
ON CONFLICT (id) DO NOTHING;
