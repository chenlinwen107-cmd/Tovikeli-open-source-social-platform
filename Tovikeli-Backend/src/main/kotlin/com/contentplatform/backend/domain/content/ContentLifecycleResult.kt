package com.contentplatform.backend.domain.content

/**
 * Typed result of a content lifecycle transition attempt.
 *
 * Used by application-layer use cases to communicate the precise outcome
 * to the API layer so the controller can map to the correct HTTP status:
 *   SUCCESS           -> 204 No Content
 *   NOT_FOUND         -> 404 Not Found
 *   INVALID_TRANSITION -> 400 Bad Request
 */
enum class ContentLifecycleResult {
    SUCCESS,
    NOT_FOUND,
    INVALID_TRANSITION,
    FORBIDDEN
}
