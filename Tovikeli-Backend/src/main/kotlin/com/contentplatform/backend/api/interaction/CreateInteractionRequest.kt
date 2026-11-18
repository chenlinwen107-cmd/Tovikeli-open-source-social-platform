package com.contentplatform.backend.api.interaction

/**
 * API request DTO for creating a Platform Interaction (Stage 20-A).
 *
 *   POST /api/v1/users/{userId}/interactions
 *
 * Both fields are required. The controller owns all validation: contentId must
 * be non-blank, type must be a valid [InteractionType] name (LIKE / FAVORITE /
 * REPOST / BOOKMARK). Missing or malformed JSON is answered by Spring MVC's
 * standard handling with HTTP 400.
 */
data class CreateInteractionRequest(
    val contentId: String?,
    val type: String?
)
