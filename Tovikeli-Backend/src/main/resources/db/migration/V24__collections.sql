CREATE TABLE collection (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    cover TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL
);

CREATE TABLE collection_item (
    id TEXT PRIMARY KEY,
    collection_id TEXT NOT NULL REFERENCES collection (id) ON DELETE CASCADE,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    CONSTRAINT ck_collection_item_target_type CHECK (target_type IN ('post', 'article')),
    CONSTRAINT uq_collection_item_target UNIQUE (collection_id, target_type, target_id)
);

CREATE INDEX idx_collection_owner_created ON collection (owner_id, created_at DESC, id DESC);
CREATE INDEX idx_collection_created ON collection (created_at DESC, id DESC);
CREATE INDEX idx_collection_item_collection_order ON collection_item (collection_id, sort_order, created_at, id);
CREATE INDEX idx_collection_item_target ON collection_item (target_type, target_id);
