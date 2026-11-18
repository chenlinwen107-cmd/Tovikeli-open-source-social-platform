-- V5: Platform User foundation (Stage 16).
--
-- Conventions (locked decisions, consistent with V1-V4):
--  * PublicId stored as TEXT PRIMARY KEY (domain identity, no DB surrogate id).
--  * Domain Long timestamps (unix ms) stored as BIGINT.
--  * Domain enums are stored as TEXT. Like the existing content/author status
--    columns, platform_user.status has NO CHECK constraint: the ACTIVE /
--    DISABLED values are owned by the UserStatus domain enum and mapped by the
--    JDBC layer. A CHECK would be redundant here (no column shape depends on
--    the enum value, unlike tag.origin).
--  * The table is named platform_user (NOT "user"): "user" is a reserved word
--    in PostgreSQL and, more importantly, an external Author is NOT a Platform
--    User — the existing `author` table stays the external-author persistence.
--
-- Username case-insensitive uniqueness:
--  * username is the platform identity marker. The FINAL guarantee for its
--    uniqueness is the expression UNIQUE index on LOWER(username):
--    "Alice" and "alice" can never coexist no matter how the application
--    layer checks first.
--  * The stored value is the verbatim (trimmed) input; only the uniqueness is
--    case-insensitive.
--
-- displayName:
--  * displayName is NOT an identity and may repeat, so it is a plain nullable
--    column with no unique constraint.

CREATE TABLE platform_user (
    id           TEXT PRIMARY KEY,
    username     TEXT NOT NULL,
    display_name TEXT,
    status       TEXT NOT NULL,
    created_at   BIGINT NOT NULL,
    updated_at   BIGINT NOT NULL
);

-- Case-insensitive username uniqueness (final guarantee, Stage 16).
CREATE UNIQUE INDEX uq_platform_user_username
    ON platform_user (LOWER(username));
