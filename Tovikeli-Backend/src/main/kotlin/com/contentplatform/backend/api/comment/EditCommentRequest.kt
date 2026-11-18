package com.contentplatform.backend.api.comment

/**
 * API request DTO for editing a Comment (Stage 37 + 40-C).
 *
 *   PATCH /api/v1/comments/{commentId}
 *
 * Fields:
 *  - [text]: required, non-blank updated comment text.
 *
 * Authentication: the caller identity is established by the server-side
 * session via the authentication filter; it is NOT accepted from query params.
 *
 * The controller owns all validation. Missing or malformed JSON is answered
 * by Spring MVC's standard handling with HTTP 400.
 */
data class EditCommentRequest(
    val text: String?
)
