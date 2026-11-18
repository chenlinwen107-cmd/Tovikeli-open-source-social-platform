-- V12: Local first-party publishing source.

INSERT INTO source (
    id, name, type, enabled
) VALUES (
    'src_local', 'Local', 'LOCAL', TRUE
) ON CONFLICT (id) DO NOTHING;

INSERT INTO instance (
    id,
    source_id,
    domain,
    display_name,
    enabled,
    allow_index,
    allow_media_preview,
    allow_media_cache,
    allow_original_access,
    nsfw_policy,
    health_status
) VALUES (
    'inst_local',
    'src_local',
    'local',
    'Local',
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    NULL,
    'HEALTHY'
) ON CONFLICT (id) DO NOTHING;
