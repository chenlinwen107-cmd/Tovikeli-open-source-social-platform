-- V2: Initial Source / Instance data (initialization seed only).
--
-- This is initialization data, not a permanent business rule.
-- The Source/Instance management workflow may replace this later.

INSERT INTO source (id, name, type, enabled) VALUES
    ('src_misskey',          'Misskey',         'MISSKEY',          TRUE),
    ('src_mastodon',         'Mastodon',        'MASTODON',         TRUE),
    ('src_wikimedia_commons','Wikimedia Commons','WIKIMEDIA_COMMONS', TRUE),
    ('src_openverse',        'Openverse',       'OPENVERSE',        TRUE),
    ('src_pixelfed',         'Pixelfed',        'PIXELFED',         TRUE);

-- Initial whitelisted instances (Misskey / Mastodon instance-whitelist model).
-- Other sources have no instance rows yet; their instance model is an
-- adapter-stage concern and is intentionally left unseeded.
INSERT INTO instance (
    id, source_id, domain, display_name, enabled,
    allow_index, allow_media_preview, allow_media_cache,
    allow_original_access, nsfw_policy, health_status
) VALUES
    ('inst_misskey_io',      'src_misskey',      'misskey.io',       'Misskey.io',      TRUE,
     TRUE, TRUE, FALSE, FALSE, NULL, 'HEALTHY'),
    ('inst_mastodon_social', 'src_mastodon',     'mastodon.social',  'Mastodon Social', TRUE,
     TRUE, TRUE, FALSE, FALSE, NULL, 'HEALTHY');
