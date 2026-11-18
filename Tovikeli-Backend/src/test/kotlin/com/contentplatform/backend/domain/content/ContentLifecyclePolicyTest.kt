package com.contentplatform.backend.domain.content

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ContentLifecyclePolicy].
 *
 * Covers all valid V1 transitions and representative invalid transitions.
 */
class ContentLifecyclePolicyTest {

    // ------------------------------------------------------------------
    // Valid transitions
    // ------------------------------------------------------------------

    @Test
    fun active_toDeleted_isAllowed() {
        assertTrue(ContentLifecyclePolicy.canTransition(ContentStatus.ACTIVE, ContentStatus.DELETED))
    }

    @Test
    fun active_toHidden_isAllowed() {
        assertTrue(ContentLifecyclePolicy.canTransition(ContentStatus.ACTIVE, ContentStatus.HIDDEN))
    }

    @Test
    fun deleted_toActive_isAllowed() {
        assertTrue(ContentLifecyclePolicy.canTransition(ContentStatus.DELETED, ContentStatus.ACTIVE))
    }

    @Test
    fun hidden_toActive_isAllowed() {
        assertTrue(ContentLifecyclePolicy.canTransition(ContentStatus.HIDDEN, ContentStatus.ACTIVE))
    }

    // ------------------------------------------------------------------
    // Invalid transitions: from ACTIVE
    // ------------------------------------------------------------------

    @Test
    fun active_toActive_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.ACTIVE, ContentStatus.ACTIVE))
    }

    @Test
    fun active_toUnavailable_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.ACTIVE, ContentStatus.UNAVAILABLE))
    }

    @Test
    fun active_toUnknown_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.ACTIVE, ContentStatus.UNKNOWN))
    }

    // ------------------------------------------------------------------
    // Invalid transitions: from DELETED
    // ------------------------------------------------------------------

    @Test
    fun deleted_toDeleted_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.DELETED, ContentStatus.DELETED))
    }

    @Test
    fun deleted_toHidden_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.DELETED, ContentStatus.HIDDEN))
    }

    @Test
    fun deleted_toUnavailable_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.DELETED, ContentStatus.UNAVAILABLE))
    }

    @Test
    fun deleted_toUnknown_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.DELETED, ContentStatus.UNKNOWN))
    }

    // ------------------------------------------------------------------
    // Invalid transitions: from HIDDEN
    // ------------------------------------------------------------------

    @Test
    fun hidden_toDeleted_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.HIDDEN, ContentStatus.DELETED))
    }

    @Test
    fun hidden_toHidden_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.HIDDEN, ContentStatus.HIDDEN))
    }

    @Test
    fun hidden_toUnavailable_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.HIDDEN, ContentStatus.UNAVAILABLE))
    }

    @Test
    fun hidden_toUnknown_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.HIDDEN, ContentStatus.UNKNOWN))
    }

    // ------------------------------------------------------------------
    // Invalid transitions: from UNAVAILABLE
    // ------------------------------------------------------------------

    @Test
    fun unavailable_toActive_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.UNAVAILABLE, ContentStatus.ACTIVE))
    }

    @Test
    fun unavailable_toDeleted_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.UNAVAILABLE, ContentStatus.DELETED))
    }

    @Test
    fun unavailable_toHidden_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.UNAVAILABLE, ContentStatus.HIDDEN))
    }

    // ------------------------------------------------------------------
    // Invalid transitions: from UNKNOWN
    // ------------------------------------------------------------------

    @Test
    fun unknown_toActive_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.UNKNOWN, ContentStatus.ACTIVE))
    }

    @Test
    fun unknown_toDeleted_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.UNKNOWN, ContentStatus.DELETED))
    }

    @Test
    fun unknown_toHidden_isInvalid() {
        assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.UNKNOWN, ContentStatus.HIDDEN))
    }
}
