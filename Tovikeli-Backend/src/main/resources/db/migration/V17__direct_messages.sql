-- V17: direct 1-to-1 messaging foundation.
CREATE TABLE direct_conversation (
    id TEXT PRIMARY KEY,
    user_a_id TEXT NOT NULL REFERENCES platform_user(id),
    user_b_id TEXT NOT NULL REFERENCES platform_user(id),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    CONSTRAINT direct_conversation_users_distinct CHECK (user_a_id <> user_b_id),
    CONSTRAINT uq_direct_conversation_users UNIQUE (user_a_id, user_b_id)
);
CREATE INDEX idx_direct_conversation_user_a ON direct_conversation (user_a_id, updated_at DESC);
CREATE INDEX idx_direct_conversation_user_b ON direct_conversation (user_b_id, updated_at DESC);

CREATE TABLE direct_message (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL REFERENCES direct_conversation(id) ON DELETE CASCADE,
    sender_user_id TEXT NOT NULL REFERENCES platform_user(id),
    body TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    read_at BIGINT,
    CONSTRAINT direct_message_body_not_blank CHECK (length(btrim(body)) > 0),
    CONSTRAINT direct_message_body_max_length CHECK (char_length(body) <= 5000)
);
CREATE INDEX idx_direct_message_conversation ON direct_message (conversation_id, created_at ASC, id ASC);
CREATE INDEX idx_direct_message_unread ON direct_message (conversation_id, read_at) WHERE read_at IS NULL;
