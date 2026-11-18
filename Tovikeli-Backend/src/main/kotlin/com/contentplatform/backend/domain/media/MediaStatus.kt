package com.contentplatform.backend.domain.media

enum class MediaStatus {
    ACTIVE,        // Available for use
    PROCESSING,    // Still being processed
    UNAVAILABLE,   // Temporarily unavailable
    DELETED,       // Permanently removed
    HIDDEN,        // Hidden from public view
    EXPIRED        // License/permissions expired
}