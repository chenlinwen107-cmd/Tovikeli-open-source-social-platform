package com.contentplatform.backend.domain.topic

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId

/** First-party Tovikeli discussion topic. Distinct from federated/imported Tag. */
data class Topic(
    val id: PublicId,
    val name: String,
    val normalizedName: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        fun create(name: String, description: String? = null): Topic {
            val display = name.trim()
            require(display.isNotBlank()) { "topic name must not be blank" }
            val normalized = normalize(display)
            return Topic(
                id = PublicId("tp_" + IdentityHash.derive("topic:$normalized")),
                name = display,
                normalizedName = normalized,
                description = description?.trim()?.takeIf { it.isNotBlank() },
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

        fun normalize(name: String): String = name.trim().lowercase()
    }
}
