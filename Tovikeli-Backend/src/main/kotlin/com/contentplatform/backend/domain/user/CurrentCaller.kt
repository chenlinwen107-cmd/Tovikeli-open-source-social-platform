package com.contentplatform.backend.domain.user

import com.contentplatform.backend.domain.common.PublicId

/**
 * Immutable snapshot of the authenticated platform user for a single HTTP request.
 *
 * Produced by the [AuthenticationFilter][com.contentplatform.backend.api.authentication.AuthenticationFilter]
 * from the server-side session, consumed by controllers via [CallerResolver][com.contentplatform.backend.api.authentication.CallerResolver].
 * Never persisted, never serialized to HTTP responses, never passed between requests.
 *
 * CurrentCaller is the ONLY source of truth for "who is calling" on protected
 * endpoints. Controllers MUST NOT extract userId from request params/paths/body
 * for authorization purposes — only CurrentCaller carries authenticated identity.
 *
 * Controllers immediately destructure `caller.userId` (a [PublicId]) and pass it
 * to use cases. CurrentCaller does NOT cross into the Application/Domain layers.
 * Use cases see `PublicId`, never `CurrentCaller`.
 *
 * The [status] field is a snapshot at authentication time. Token status is
 * authoritative for the request duration; no mid-request DB re-check is required.
 */
data class CurrentCaller(
    val userId: PublicId,
    val username: String,
    val status: UserStatus
) {
    init {
        require(status == UserStatus.ACTIVE) {
            "CurrentCaller can only represent ACTIVE users; DISABLED users are rejected at the authentication boundary"
        }
    }
}
