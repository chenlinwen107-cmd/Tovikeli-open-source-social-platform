package com.contentplatform.backend.domain.interaction

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Domain tests for the [Interaction] aggregate (Stage 17).
 */
class InteractionTest {

    private val user = PublicId("usr_alice")
    private val otherUser = PublicId("usr_bob")
    private val content = PublicId("ct_1")
    private val otherContent = PublicId("ct_2")

    @Test
    fun create_setsFieldsAndDerivesIntPrefixedDeterministicId() {
        val interaction = Interaction.create(user, content, InteractionType.LIKE)

        assertTrue(interaction.id.value.startsWith("int_"), "Interaction PublicId prefix must be int_")
        assertEquals(
            "int_" + IdentityHash.derive("${user.value}:${content.value}:LIKE"),
            interaction.id.value
        )
        assertEquals(user, interaction.userId)
        assertEquals(content, interaction.contentId)
        assertEquals(InteractionType.LIKE, interaction.type)
        assertTrue(interaction.createdAt > 0, "createdAt must be a positive unix-ms timestamp")
    }

    @Test
    fun create_sameIdentityTuple_alwaysProducesTheSameId() {
        val first = Interaction.create(user, content, InteractionType.FAVORITE)
        val second = Interaction.create(user, content, InteractionType.FAVORITE)

        assertEquals(first.id, second.id)
        assertEquals(first, second.copy(createdAt = first.createdAt))
    }

    @Test
    fun create_differentUser_producesDifferentId() {
        val a = Interaction.create(user, content, InteractionType.LIKE)
        val b = Interaction.create(otherUser, content, InteractionType.LIKE)

        assertNotEquals(a.id, b.id)
    }

    @Test
    fun create_differentContent_producesDifferentId() {
        val a = Interaction.create(user, content, InteractionType.LIKE)
        val b = Interaction.create(user, otherContent, InteractionType.LIKE)

        assertNotEquals(a.id, b.id)
    }

    @Test
    fun create_differentType_producesDifferentId() {
        val like = Interaction.create(user, content, InteractionType.LIKE)
        val bookmark = Interaction.create(user, content, InteractionType.BOOKMARK)

        assertNotEquals(like.id, bookmark.id, "type must participate in the deterministic identity")
    }

    @Test
    fun create_supportsAllFourInteractionTypes() {
        InteractionType.entries.forEach { type ->
            val interaction = Interaction.create(user, content, type)
            assertTrue(interaction.id.value.startsWith("int_"))
            assertEquals(type, interaction.type)
        }
    }

    @Test
    fun blankUserId_isRejectedByPublicIdInvariant() {
        assertFailsWith<IllegalArgumentException> {
            Interaction.create(PublicId(""), content, InteractionType.LIKE)
        }
    }

    @Test
    fun blankContentId_isRejectedByPublicIdInvariant() {
        assertFailsWith<IllegalArgumentException> {
            Interaction.create(user, PublicId(""), InteractionType.LIKE)
        }
    }
}
