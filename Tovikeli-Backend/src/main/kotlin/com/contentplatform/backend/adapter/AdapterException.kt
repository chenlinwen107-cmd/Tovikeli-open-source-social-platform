package com.contentplatform.backend.adapter

/**
 * Error abstraction at the External Adapter boundary.
 *
 * A concrete adapter wraps its platform-specific failures (HTTP errors, SDK
 * exceptions, malformed payloads) into this exception before they can cross
 * the Adapter boundary. Neither the Domain layer nor the persistence layer
 * ever sees HTTP/SDK/platform exception types.
 *
 * - pure Kotlin unchecked exception (extends [RuntimeException])
 * - preserves [message]
 * - supports [cause]
 * - no Spring, no HTTP client, no SDK dependency
 *
 * No retry framework is built around this in the current stage; callers may
 * decide to skip/log a failed fetch.
 */
class AdapterException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
