CREATE TABLE community_event (
    id TEXT PRIMARY KEY,
    organizer_id TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT,
    title TEXT NOT NULL,
    cover TEXT NOT NULL DEFAULT '',
    location TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL,
    start_at BIGINT NOT NULL,
    end_at BIGINT NOT NULL,
    capacity INT NOT NULL DEFAULT 0,
    fee NUMERIC(12,2) NOT NULL DEFAULT 0,
    online BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    CONSTRAINT ck_community_event_capacity CHECK (capacity >= 0),
    CONSTRAINT ck_community_event_fee CHECK (fee >= 0),
    CONSTRAINT ck_community_event_time CHECK (end_at > start_at)
);
CREATE INDEX idx_community_event_start ON community_event (start_at DESC, id DESC);
CREATE INDEX idx_community_event_category_start ON community_event (category, start_at DESC, id DESC);
CREATE INDEX idx_community_event_organizer ON community_event (organizer_id, created_at DESC, id DESC);

CREATE TABLE community_event_signup (
    id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL REFERENCES community_event (id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT,
    created_at BIGINT NOT NULL,
    CONSTRAINT uq_community_event_signup UNIQUE (event_id, user_id)
);
CREATE INDEX idx_community_event_signup_event ON community_event_signup (event_id, created_at ASC, id ASC);
CREATE INDEX idx_community_event_signup_user ON community_event_signup (user_id, created_at DESC, id DESC);