package com.contentplatform.backend.domain.common

import java.security.MessageDigest

/**
 * Deterministic SHA-256 based identity hashing.
 *
 * Used to derive stable PublicId values from natural keys.
 *
 * Guarantees:
 * - SHA-256 via the Java 21 standard library (MessageDigest)
 * - lowercase hexadecimal output
 * - truncated to the first 32 hex characters
 *
 * No external dependencies.
 */
object IdentityHash {

    private const val HEX_CHARS = "0123456789abcdef"
    private const val PREFIX_HEX_LENGTH = 32

    /**
     * Derives a stable 32-character lowercase hex value from [input].
     *
     * The same input always produces the same output. Different inputs are
     * expected to produce different outputs with negligible collision risk.
     */
    fun derive(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            HEX_CHARS[value ushr 4].toString() + HEX_CHARS[value and 0x0F]
        }
        return hex.take(PREFIX_HEX_LENGTH)
    }
}
