package com.contentplatform.backend.api.authentication

/**
 * Keys used by the HTTP session authentication boundary.
 *
 * Only the authenticated platform-user identity is stored in session.
 * Domain objects, password hashes, SecurityContext objects, and other
 * authentication internals are intentionally excluded.
 */
object SessionAttributes {

    const val CURRENT_USER_ID = "currentUserId"
}
