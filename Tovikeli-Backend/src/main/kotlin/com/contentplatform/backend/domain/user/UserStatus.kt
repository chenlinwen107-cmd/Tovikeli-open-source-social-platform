package com.contentplatform.backend.domain.user

/**
 * Lifecycle status of a Platform [User].
 *
 * Stage 16 Platform User Foundation only supports these two states:
 *  - ACTIVE:   the user is a fully usable platform identity;
 *  - DISABLED: the user identity is disabled.
 *
 * A newly created platform user is always ACTIVE. No status-transition use
 * case exists in Stage 16; the enum is the domain contract the persistence
 * layer maps to the `platform_user.status` TEXT column.
 */
enum class UserStatus {
    ACTIVE,
    DISABLED
}
