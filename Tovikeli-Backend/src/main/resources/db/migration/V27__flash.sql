CREATE TABLE flash (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    summary TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL,
    url TEXT NOT NULL DEFAULT '',
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_flash_published ON flash (pinned DESC, created_at DESC, id DESC);
CREATE INDEX idx_flash_category ON flash (category, pinned DESC, created_at DESC, id DESC);