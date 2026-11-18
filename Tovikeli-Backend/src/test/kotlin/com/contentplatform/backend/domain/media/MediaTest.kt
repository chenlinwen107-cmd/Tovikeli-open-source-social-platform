package com.contentplatform.backend.domain.media

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MediaTest {

    // ------------------------------------------------------------------
    // isAvailable
    // ------------------------------------------------------------------

    @Test
    fun isAvailable_activeAndSourceEnabled_returnsTrue() {
        val media = sampleMedia(status = MediaStatus.ACTIVE)

        assertTrue(media.isAvailable(sourceEnabled = true))
    }

    @Test
    fun isAvailable_nonActiveStatus_returnsFalse() {
        for (status in listOf(
            MediaStatus.PROCESSING,
            MediaStatus.UNAVAILABLE,
            MediaStatus.DELETED,
            MediaStatus.HIDDEN,
            MediaStatus.EXPIRED
        )) {
            val media = sampleMedia(status = status)
            assertFalse(media.isAvailable(sourceEnabled = true), "status=$status should not be available")
        }
    }

    @Test
    fun isAvailable_activeButSourceDisabled_returnsFalse() {
        val media = sampleMedia(status = MediaStatus.ACTIVE)

        assertFalse(media.isAvailable(sourceEnabled = false))
    }

    // ------------------------------------------------------------------
    // canAccessOriginal
    // ------------------------------------------------------------------

    @Test
    fun canAccessOriginal_activeSourceEnabledAndAllPoliciesTrue_returnsTrue() {
        val media = sampleMedia(status = MediaStatus.ACTIVE)

        assertTrue(
            media.canAccessOriginal(
                sourceEnabled = true,
                instanceAllowOriginalAccess = true,
                sourceLicenseRestrictions = true,
                apiAvailable = true,
                apiExposurePolicy = true
            )
        )
    }

    @Test
    fun canAccessOriginal_activeButSourceDisabled_returnsFalse() {
        val media = sampleMedia(status = MediaStatus.ACTIVE)

        assertFalse(
            media.canAccessOriginal(
                sourceEnabled = false,
                instanceAllowOriginalAccess = true,
                sourceLicenseRestrictions = true,
                apiAvailable = true,
                apiExposurePolicy = true
            )
        )
    }

    @Test
    fun canAccessOriginal_instanceAllowOriginalAccessFalse_returnsFalse() {
        val media = sampleMedia(status = MediaStatus.ACTIVE)

        assertFalse(
            media.canAccessOriginal(
                sourceEnabled = true,
                instanceAllowOriginalAccess = false,
                sourceLicenseRestrictions = true,
                apiAvailable = true,
                apiExposurePolicy = true
            )
        )
    }

    @Test
    fun canAccessOriginal_sourceLicenseRestrictionsFalse_returnsFalse() {
        val media = sampleMedia(status = MediaStatus.ACTIVE)

        assertFalse(
            media.canAccessOriginal(
                sourceEnabled = true,
                instanceAllowOriginalAccess = true,
                sourceLicenseRestrictions = false,
                apiAvailable = true,
                apiExposurePolicy = true
            )
        )
    }

    @Test
    fun canAccessOriginal_apiAvailableFalse_returnsFalse() {
        val media = sampleMedia(status = MediaStatus.ACTIVE)

        assertFalse(
            media.canAccessOriginal(
                sourceEnabled = true,
                instanceAllowOriginalAccess = true,
                sourceLicenseRestrictions = true,
                apiAvailable = false,
                apiExposurePolicy = true
            )
        )
    }

    @Test
    fun canAccessOriginal_apiExposurePolicyFalse_returnsFalse() {
        val media = sampleMedia(status = MediaStatus.ACTIVE)

        assertFalse(
            media.canAccessOriginal(
                sourceEnabled = true,
                instanceAllowOriginalAccess = true,
                sourceLicenseRestrictions = true,
                apiAvailable = true,
                apiExposurePolicy = false
            )
        )
    }

    @Test
    fun canAccessOriginal_nonActiveStatusSourceEnabled_returnsFalse() {
        for (status in listOf(
            MediaStatus.PROCESSING,
            MediaStatus.UNAVAILABLE,
            MediaStatus.DELETED,
            MediaStatus.HIDDEN,
            MediaStatus.EXPIRED
        )) {
            val media = sampleMedia(status = status)

            assertFalse(
                media.canAccessOriginal(
                    sourceEnabled = true,
                    instanceAllowOriginalAccess = true,
                    sourceLicenseRestrictions = true,
                    apiAvailable = true,
                    apiExposurePolicy = true
                ),
                "status=$status should not allow original access"
            )
        }
    }

    @Test
    fun canAccessOriginal_nonActiveStatusSourceDisabled_returnsFalse() {
        for (status in listOf(
            MediaStatus.PROCESSING,
            MediaStatus.UNAVAILABLE,
            MediaStatus.DELETED,
            MediaStatus.HIDDEN,
            MediaStatus.EXPIRED
        )) {
            val media = sampleMedia(status = status)

            assertFalse(
                media.canAccessOriginal(
                    sourceEnabled = false,
                    instanceAllowOriginalAccess = true,
                    sourceLicenseRestrictions = true,
                    apiAvailable = true,
                    apiExposurePolicy = true
                ),
                "status=$status should not allow original access"
            )
        }
    }

    // ------------------------------------------------------------------
    // canProvidePreview
    // ------------------------------------------------------------------

    @Test
    fun canProvidePreview_activeSourceEnabledAndAllowPreview_returnsTrue() {
        val media = sampleMedia(status = MediaStatus.ACTIVE)

        assertTrue(media.canProvidePreview(sourceEnabled = true, instanceAllowPreview = true))
    }

    @Test
    fun canProvidePreview_activeSourceEnabledButDisallowedPreview_returnsFalse() {
        val media = sampleMedia(status = MediaStatus.ACTIVE)

        assertFalse(media.canProvidePreview(sourceEnabled = true, instanceAllowPreview = false))
    }

    @Test
    fun canProvidePreview_activeButSourceDisabled_returnsFalse() {
        val media = sampleMedia(status = MediaStatus.ACTIVE)

        assertFalse(media.canProvidePreview(sourceEnabled = false, instanceAllowPreview = true))
    }

    @Test
    fun canProvidePreview_nonActiveStatusSourceEnabled_returnsFalse() {
        for (status in listOf(
            MediaStatus.PROCESSING,
            MediaStatus.UNAVAILABLE,
            MediaStatus.DELETED,
            MediaStatus.HIDDEN,
            MediaStatus.EXPIRED
        )) {
            val media = sampleMedia(status = status)

            assertFalse(
                media.canProvidePreview(sourceEnabled = true, instanceAllowPreview = true),
                "status=$status should not provide preview"
            )
        }
    }

    // ------------------------------------------------------------------
    // updateStatus
    // ------------------------------------------------------------------

    @Test
    fun updateStatus_returnsNewMediaWithUpdatedStatus() {
        val original = sampleMedia(status = MediaStatus.ACTIVE)
        val updated = original.updateStatus(MediaStatus.HIDDEN)

        assertNotEquals(original, updated)
        assertEquals(MediaStatus.HIDDEN, updated.status)
    }

    @Test
    fun updateStatus_doesNotMutateOriginal() {
        val original = sampleMedia(status = MediaStatus.ACTIVE)
        val before = original.updatedAt

        original.updateStatus(MediaStatus.DELETED)

        assertEquals(MediaStatus.ACTIVE, original.status)
        assertEquals(before, original.updatedAt)
    }

    @Test
    fun updateStatus_refreshesUpdatedAt() {
        val original = sampleMedia(status = MediaStatus.ACTIVE)
        val before = System.currentTimeMillis()

        val updated = original.updateStatus(MediaStatus.EXPIRED)

        val after = System.currentTimeMillis()
        assertTrue(updated.updatedAt >= before && updated.updatedAt <= after)
        assertTrue(updated.updatedAt >= original.updatedAt)
    }

    @Test
    fun updateStatus_preservesOtherFields() {
        val original = sampleMedia(
            sha256 = "a".repeat(64),
            status = MediaStatus.ACTIVE
        )
        val updated = original.updateStatus(MediaStatus.UNAVAILABLE)

        assertEquals(original.id, updated.id)
        assertEquals(original.sha256, updated.sha256)
        assertEquals(original.sourceId, updated.sourceId)
        assertEquals(original.instanceId, updated.instanceId)
        assertEquals(original.thumbnail, updated.thumbnail)
        assertEquals(original.preview, updated.preview)
        assertEquals(original.original, updated.original)
        assertEquals(original.altText, updated.altText)
        assertEquals(original.license, updated.license)
        assertEquals(original.metadata, updated.metadata)
        assertEquals(original.createdAt, updated.createdAt)
    }

    // ------------------------------------------------------------------
    // isExpired
    // ------------------------------------------------------------------

    @Test
    fun isExpired_expired_returnsTrue() {
        val media = sampleMedia(status = MediaStatus.EXPIRED)
        assertTrue(media.isExpired())
    }

    @Test
    fun isExpired_deleted_returnsTrue() {
        val media = sampleMedia(status = MediaStatus.DELETED)
        assertTrue(media.isExpired())
    }

    @Test
    fun isExpired_otherStatuses_returnsFalse() {
        for (status in listOf(
            MediaStatus.ACTIVE,
            MediaStatus.PROCESSING,
            MediaStatus.UNAVAILABLE,
            MediaStatus.HIDDEN
        )) {
            val media = sampleMedia(status = status)
            assertFalse(media.isExpired(), "status=$status should not be expired")
        }
    }

    // ------------------------------------------------------------------
    // getDeduplicationKey
    // ------------------------------------------------------------------

    @Test
    fun getDeduplicationKey_withSha256_returnsSha256() {
        val sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val media = sampleMedia(sha256 = sha)

        assertEquals(sha, media.getDeduplicationKey())
    }

    @Test
    fun getDeduplicationKey_withNullSha256_returnsNull() {
        val media = sampleMedia(sha256 = null)

        assertNull(media.getDeduplicationKey())
    }

    // ------------------------------------------------------------------
    // createFromAdapter (Stage 11): deterministic Media identity
    // ------------------------------------------------------------------

    @Test
    fun createFromAdapter_derivesDeterministicMdIdFromInstanceAndRef() {
        val first = Media.createFromAdapter(
            instanceId = PublicId("inst_a"),
            externalMediaRef = "m1",
            sourceId = PublicId("src_x")
        )
        val second = Media.createFromAdapter(
            instanceId = PublicId("inst_a"),
            externalMediaRef = "m1",
            sourceId = PublicId("src_x")
        )

        assertEquals("md_" + IdentityHash.derive("inst_a:m1"), first.id.value)
        assertEquals(first.id, second.id, "same (instance, ref) must always produce the same id")
        assertTrue(first.id.value.startsWith("md_"))
    }

    @Test
    fun createFromAdapter_isolatesMediaIdentityByInstance() {
        val a = Media.createFromAdapter(
            instanceId = PublicId("inst_a"),
            externalMediaRef = "m1",
            sourceId = PublicId("src_x")
        )
        val b = Media.createFromAdapter(
            instanceId = PublicId("inst_b"),
            externalMediaRef = "m1",
            sourceId = PublicId("src_x")
        )

        assertNotEquals(a.id, b.id, "same external ref on different instances must not collide")
    }

    @Test
    fun createFromAdapter_distinguishesDifferentExternalRefsOnSameInstance() {
        val a = Media.createFromAdapter(
            instanceId = PublicId("inst_a"),
            externalMediaRef = "m1",
            sourceId = PublicId("src_x")
        )
        val b = Media.createFromAdapter(
            instanceId = PublicId("inst_a"),
            externalMediaRef = "m2",
            sourceId = PublicId("src_x")
        )

        assertNotEquals(a.id, b.id, "different external refs on the same instance must differ")
    }

    @Test
    fun createFromAdapter_sha256NeverParticipatesInIdentity() {
        val withSha = Media.createFromAdapter(
            instanceId = PublicId("inst_a"),
            externalMediaRef = "m1",
            sourceId = PublicId("src_x"),
            sha256 = "abc"
        )
        val withoutSha = Media.createFromAdapter(
            instanceId = PublicId("inst_a"),
            externalMediaRef = "m1",
            sourceId = PublicId("src_x"),
            sha256 = null
        )

        assertEquals(withSha.id, withoutSha.id, "sha256 is a dedup candidate, never an identity input")
        assertEquals("abc", withSha.sha256)
        assertNull(withoutSha.sha256)
    }

    @Test
    fun createFromAdapter_setsContextIdentityAndActiveLifecycle() {
        val media = Media.createFromAdapter(
            instanceId = PublicId("inst_a"),
            externalMediaRef = "m1",
            sourceId = PublicId("src_x"),
            sha256 = "abc",
            preview = MediaUrl("https://x/p.png"),
            original = MediaUrl("https://x/o.png"),
            altText = "alt",
            license = "CC-BY-4.0",
            metadata = mapOf("width" to 1600)
        )

        assertEquals(PublicId("src_x"), media.sourceId)
        assertEquals(PublicId("inst_a"), media.instanceId)
        assertEquals(MediaStatus.ACTIVE, media.status)
        assertEquals("https://x/p.png", media.preview?.value)
        assertEquals("https://x/o.png", media.original?.value)
        assertEquals("alt", media.altText)
        assertEquals("CC-BY-4.0", media.license)
        assertEquals(mapOf("width" to 1600), media.metadata)
        assertNull(media.thumbnail)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun sampleMedia(
        id: PublicId = PublicId("media-test"),
        sha256: String? = null,
        sourceId: PublicId = PublicId("source-test"),
        instanceId: PublicId = PublicId("instance-test"),
        status: MediaStatus = MediaStatus.ACTIVE,
        createdAt: Long = 1_700_000_000_000L,
        updatedAt: Long = 1_700_000_000_000L
    ): Media {
        return Media(
            id = id,
            sha256 = sha256,
            sourceId = sourceId,
            instanceId = instanceId,
            thumbnail = MediaUrl("https://example.com/thumbnail.jpg"),
            preview = MediaUrl("https://example.com/preview.jpg"),
            original = MediaUrl("https://example.com/original.jpg"),
            altText = "test alt text",
            license = "CC-BY-4.0",
            metadata = mapOf(),
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
