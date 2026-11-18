CREATE TABLE article (
    id TEXT PRIMARY KEY REFERENCES content (id) ON DELETE RESTRICT,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    cover TEXT NOT NULL DEFAULT '',
    summary TEXT NOT NULL DEFAULT '',
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    views BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_article_category_latest ON article (category, created_at DESC, id DESC);
CREATE INDEX idx_article_latest ON article (created_at DESC, id DESC);
CREATE INDEX idx_article_featured ON article (featured, created_at DESC, id DESC);
