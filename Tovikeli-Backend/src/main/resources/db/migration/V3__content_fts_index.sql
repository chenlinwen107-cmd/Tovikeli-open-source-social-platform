-- V3: Full-text search support for public content bodies (Stage 15-A).
--
-- The Stage 15-A search query matches content.text through
--
--   to_tsvector('english', COALESCE(c.text, ''))
--     @@ websearch_to_tsquery('english', ?)
--
-- and ranks with ts_rank over the same expression. This GIN expression index
-- uses exactly that expression (same regconfig 'english', same COALESCE, same
-- column) so the planner can use it for the tsvector @@ match. content.text is
-- nullable, so COALESCE keeps null rows matchable but unranked-empty.
CREATE INDEX idx_content_text_fts
    ON content
    USING GIN (to_tsvector('english', COALESCE(text, '')));
