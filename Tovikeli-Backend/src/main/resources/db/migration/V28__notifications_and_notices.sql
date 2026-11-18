CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    recipient_id TEXT NOT NULL REFERENCES platform_user(id) ON DELETE CASCADE,
    actor_id TEXT REFERENCES platform_user(id) ON DELETE SET NULL,
    type TEXT NOT NULL,
    target_type TEXT NOT NULL DEFAULT '',
    target_id TEXT NOT NULL DEFAULT '',
    preview TEXT NOT NULL DEFAULT '',
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_notification_recipient_created ON notification (recipient_id, created_at DESC, id DESC);
CREATE INDEX idx_notification_recipient_unread ON notification (recipient_id, read, created_at DESC, id DESC);

CREATE TABLE site_notice (
    id BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    body TEXT NOT NULL DEFAULT '',
    level TEXT NOT NULL DEFAULT 'info',
    link TEXT NOT NULL DEFAULT '',
    link_label TEXT NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_site_notice_active ON site_notice (active, pinned DESC, created_at DESC, id DESC);
