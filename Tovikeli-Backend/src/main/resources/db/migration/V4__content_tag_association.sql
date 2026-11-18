-- V4: Content <-> Tag many-to-many association (Stage 15-B).
--
-- The association is intentionally minimal:
--  * no position / weight / ranking / confidence columns;
--  * PRIMARY KEY (content_id, tag_id) makes re-attachment idempotent through
--    ON CONFLICT DO NOTHING;
--  * ON DELETE RESTRICT mirrors every other FK in the schema (logical deletes
--    via domain status, never physical deletes);
--  * idx_content_tag_tag_id supports the reverse (tag -> content) direction
--    when tag-scoped queries arrive in a later stage.

CREATE TABLE content_tag (
    content_id TEXT NOT NULL REFERENCES content (id) ON DELETE RESTRICT,
    tag_id     TEXT NOT NULL REFERENCES tag (id) ON DELETE RESTRICT,
    PRIMARY KEY (content_id, tag_id)
);

CREATE INDEX idx_content_tag_tag_id
    ON content_tag (tag_id);
