package com.contentplatform.backend.api.comment

/**
 * API request DTO for creating a Platform Comment (Stage 21-A + 40-C).
 *
 *   POST /api/v1/contents/{contentId}/comments
 *
 * Fields:
 *  - [text]: required, non-blank comment text.
 *  - [parentCommentId]: optional; null for top-level comments, a cmt_xxx
 *    public ID for replies.
 *
 * Authentication: the caller identity is established by the server-side
 * session via the authentication filter; it is NOT part of the request body.
 *
 * The controller owns all validation. Missing or malformed JSON is answered
 * by Spring MVC's standard handling with HTTP 400.
 */
data class CreateCommentRequest(
    val text: String?,
    val parentCommentId: String?
)
