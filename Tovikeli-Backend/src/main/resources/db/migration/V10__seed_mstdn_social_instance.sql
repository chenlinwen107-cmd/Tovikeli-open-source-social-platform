-- V10: Additional Mastodon source instance (P8-F).
--
-- Adds mstdn.social as a second whitelisted Mastodon instance so the existing
-- anonymous MastodonAdapter can ingest real public content (mastodon.social's
-- anonymous public timeline is disabled and answers HTTP 422; see P8-E).
--
-- Additive only: V1-V9 are frozen. The 'src_mastodon' source and the existing
-- 'inst_mastodon_social' row are NOT modified or deleted.
--
-- The flags mirror the existing Mastodon instance contract exactly:
--   enabled = TRUE, allow_index = TRUE, allow_media_preview = TRUE,
--   allow_media_cache = FALSE, allow_original_access = FALSE,
--   nsfw_policy = NULL, health_status = 'HEALTHY'.
--
-- Like V2 this is initialization seed data applied once by Flyway; no
-- UPDATE/DELETE and no schema change.
INSERT INTO instance (
    id, source_id, domain, display_name, enabled,
    allow_index, allow_media_preview, allow_media_cache,
    allow_original_access, nsfw_policy, health_status
) VALUES
    ('inst_mastodon_mstdn_social', 'src_mastodon', 'mstdn.social', 'mstdn.social', TRUE,
     TRUE, TRUE, FALSE, FALSE, NULL, 'HEALTHY');
