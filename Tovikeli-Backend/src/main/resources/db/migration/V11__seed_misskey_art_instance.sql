-- V11: misskey.art whitelisted instance (P9-F).
--
-- Adds misskey.art as a Misskey instance so the new MisskeyAdapter can ingest
-- real public local-timeline content (illustration / creator-heavy instance)
-- through the existing ingestion pipeline.
--
-- Additive only: V1-V10 are frozen. The 'src_misskey' source and the existing
-- 'inst_misskey_io' row are NOT modified or deleted.
--
-- The flags mirror the current public-content instance contract exactly:
--   enabled = TRUE, allow_index = TRUE, allow_media_preview = TRUE,
--   allow_media_cache = FALSE, allow_original_access = FALSE,
--   nsfw_policy = NULL, health_status = 'HEALTHY'.
--
-- sensitive / CW safety is enforced at the MisskeyAdapter boundary (P9-F1
-- approved decision A); no nsfw_policy value is introduced in this stage.
--
-- Like V2/V10 this is initialization seed data applied once by Flyway; no
-- UPDATE/DELETE and no schema change.
INSERT INTO instance (
    id, source_id, domain, display_name, enabled,
    allow_index, allow_media_preview, allow_media_cache,
    allow_original_access, nsfw_policy, health_status
) VALUES
    ('inst_misskey_art', 'src_misskey', 'misskey.art', 'misskey.art', TRUE,
     TRUE, TRUE, FALSE, FALSE, NULL, 'HEALTHY');
