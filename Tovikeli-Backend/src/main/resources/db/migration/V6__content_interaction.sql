-- V6: Content Interaction foundation (Stage 17).
--
-- Conventions (locked decisions, consistent with V1-V5):
--  * PublicId stored as TEXT PRIMARY KEY (domain identity, no DB surrogate id).
--  * Domain Long timestamps (unix ms) stored as BIGINT.
--  * Domain enums are stored as TEXT. Like every other enum-TEXT column in the
--    schema (content.status, platform_user.status, ...), content_interaction.type
--    has NO CHECK constraint: the LIKE/FAVORITE/REPOST/BOOKMARK values are owned
--    by the InteractionType domain enum and mapped by the JDBC layer.
--  * All FKs use ON DELETE RESTRICT, mirroring the rest of the schema.
--
-- Interaction identity (domain + DB):
--  * The natural key is (user_id, content_id, type): a user interacts with one
--    content at most once per type. The UNIQUE constraint on that triple is the
--    FINAL concurrency guarantee (no duplicate rows, ever).
--  * PublicId is stored as TEXT PRIMARY KEY (int_ prefix); no DB surrogate.
--
-- Access-pattern indexes:
--  * (user_id, created_at DESC)              -> per-user interaction listing.
--  * (content_id, type, created_at DESC)     -> per-content / per-type views.
--  Both are plain (non-unique) indexes; uniqueness lives in the UNIQUE
--  constraint, not in these access indexes.

CREATE TABLE content_interaction (
    id         TEXT PRIMARY KEY,
    user_id    TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT,
    content_id TEXT NOT NULL REFERENCES content (id) ON DELETE RESTRICT,
    type       TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    CONSTRAINT uq_content_interaction_identity UNIQUE (user_id, content_id, type)
);

CREATE INDEX idx_content_interaction_user
    ON content_interaction (user_id, created_at DESC);

CREATE INDEX idx_content_interaction_content_type
    ON content_interaction (content_id, type, created_at DESC);
