package com.contentplatform.backend.domain.user

import com.contentplatform.backend.domain.common.IdentityHash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Domain tests for the Platform [User] aggregate (Stage 16).
 */
class UserTest {

    @Test
    fun create_assignsActiveUserWithUsrPrefixedDeterministicId() {
        val user = User.create(username = "alice", displayName = "Alice Example")

        assertTrue(user.id.value.startsWith("usr_"), "User PublicId prefix must be usr_")
        assertEquals("usr_" + IdentityHash.derive("alice"), user.id.value)
        assertEquals("alice", user.username)
        assertEquals("Alice Example", user.displayName)
        assertEquals(UserStatus.ACTIVE, user.status, "a newly created user must default to ACTIVE")
        assertTrue(user.createdAt > 0)
        assertEquals(user.createdAt, user.updatedAt, "createdAt and updatedAt must be equal at creation")
    }

    @Test
    fun create_sameUsername_producesSameDeterministicPublicId() {
        val first = User.create(username = "alice", displayName = null)
        val second = User.create(username = "alice", displayName = "Different display")

        // displayName is NOT identity: it must not change the PublicId.
        assertEquals(first.id, second.id)
    }

    @Test
    fun create_keepsStoredUsernameVerbatimIncludingCase() {
        val user = User.create(username = "Alice", displayName = null)

        assertEquals("Alice", user.username, "the stored username keeps its exact casing")
    }

    @Test
    fun create_withoutDisplayName_keepsItNull() {
        val user = User.create(username = "bob", displayName = null)

        assertEquals(null, user.displayName)
    }

    @Test
    fun create_blankUsername_throwsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> { User.create(username = "   ", displayName = null) }
        assertFailsWith<IllegalArgumentException> { User.create(username = "", displayName = null) }
    }

    @Test
    fun userStatus_supportsExactlyActiveAndDisabled() {
        assertEquals(setOf("ACTIVE", "DISABLED"), UserStatus.entries.map { it.name }.toSet())
    }
}
