package com.contentplatform.backend.adapter

import kotlin.reflect.full.memberProperties
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the DTO contract constraints that are checkable structurally:
 * no PublicId / DB / persistence fields, no identity-deriving members.
 */
class AdapterContractStructuralTest {

    private fun fieldNames(type: kotlin.reflect.KClass<*>): Set<String> =
        type.memberProperties.map { it.name }.toSet()

    @Test
    fun authorInput_hasNoPublicIdOrRepositoryOrEntityField() {
        val fields = fieldNames(ExternalAuthorInput::class)
        assertFalse(fields.contains("id"), "no id field")
        assertFalse(fields.contains("publicId"), "no publicId")
        assertTrue(fields.containsAll(setOf("instanceRef", "sourceAuthorId", "name", "username", "metadata")))
    }

    @Test
    fun mediaInput_hasNoIdentityField() {
        val fields = fieldNames(ExternalMediaInput::class)
        // sha256 is present but only as dedup candidate; there is no id/publicId/md_.
        assertFalse(fields.contains("id"))
        assertFalse(fields.contains("publicId"))
        assertFalse(fields.any { it.startsWith("md_") || it.contains("mediaId") })
        assertTrue(fields.contains("sha256"))
        assertTrue(fields.contains("metadata"))
    }

    @Test
    fun contentInput_hasNoSystemLifecycleOrPolicyFields() {
        val fields = fieldNames(ExternalContentInput::class)
        // must NOT carry createdAt / updatedAt / status / policy
        assertFalse(fields.contains("createdAt"))
        assertFalse(fields.contains("updatedAt"))
        assertFalse(fields.contains("status"))
        assertFalse(fields.contains("allowIndex"))
        assertFalse(fields.contains("enabled"))
        assertFalse(fields.contains("healthStatus"))
        assertFalse(fields.contains("publicId"))
    }

    @Test
    fun tagInput_hasNoDomainIdentityField() {
        val fields = fieldNames(ExternalTagInput::class)
        assertFalse(fields.contains("id"))
        assertFalse(fields.contains("publicId"))
        // name and normalizedName are the tag facts
        assertTrue(fields.contains("name"))
        assertTrue(fields.contains("normalizedName"))
    }

    @Test
    fun fetchCommand_hasNoDatabaseIdentifier() {
        val fields = fieldNames(FetchCommand::class)
        assertFalse(fields.contains("sourceId"), "no DB source id")
        assertFalse(fields.contains("instanceId"), "no DB instance id")
        assertTrue(fields.contains("sourceType"))
        assertTrue(fields.contains("instanceDomain"))
    }

    @Test
    fun refs_carryNoDatabaseIdentifier() {
        assertTrue(fieldNames(ExternalInstanceRef::class).containsAll(setOf("instanceDomain")))
        assertFalse(fieldNames(ExternalInstanceRef::class).contains("id"))
        assertTrue(fieldNames(ExternalSourceRef::class).contains("sourceType"))
        assertFalse(fieldNames(ExternalSourceRef::class).contains("id"))
    }
}
