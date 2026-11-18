CREATE TABLE nav_category (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    icon TEXT NOT NULL DEFAULT 'compass',
    sort_order INT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX uq_nav_category_name ON nav_category (LOWER(name));
CREATE INDEX idx_nav_category_sort ON nav_category (sort_order ASC, id ASC);

CREATE TABLE nav_link (
    id TEXT PRIMARY KEY,
    category_id TEXT NOT NULL REFERENCES nav_category(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    color TEXT NOT NULL DEFAULT '#2b54f0',
    clicks BIGINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_nav_link_category_sort ON nav_link (category_id, sort_order ASC, id ASC);
CREATE INDEX idx_nav_link_popular ON nav_link (clicks DESC, id ASC);

CREATE TABLE user_nav_link (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES platform_user(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX uq_user_nav_link_url ON user_nav_link (user_id, LOWER(url));
CREATE INDEX idx_user_nav_link_user_created ON user_nav_link (user_id, created_at DESC, id DESC);
