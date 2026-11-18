package com.contentplatform.backend.domain.interaction

/**
 * Platform Interaction type (Stage 17 Platform Interaction Foundation).
 *
 * Only these four types exist in Stage 17:
 *  - LIKE
 *  - FAVORITE
 *  - REPOST
 *  - BOOKMARK
 *  - LOVE / HAHA / WOW / SUPPORT are mutually-exclusive reaction types.
 *
 * COMMENT, notifications, follow and social graph concepts are explicitly out
 * of scope and do not exist in this enum.
 *
 * NOTE: this is intentionally a NEW enum independent of the legacy
 * [PlatformInteractionType] (which additionally carries COMMENT from earlier
 * stages). Stage 17 interacts only with [InteractionType]; the legacy enum is
 * frozen and untouched.
 */
enum class InteractionType {
    LIKE,
    FAVORITE,
    REPOST,
    BOOKMARK
}
