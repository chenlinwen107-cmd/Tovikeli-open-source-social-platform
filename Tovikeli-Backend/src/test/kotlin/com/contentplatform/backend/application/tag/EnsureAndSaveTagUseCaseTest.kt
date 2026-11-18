package com.contentplatform.backend.application.tag

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.domain.tag.TagOrigin
import com.contentplatform.backend.repository.TagRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class EnsureAndSaveTagUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fake TagRepository
    // ------------------------------------------------------------------

    private class FakeTagRepository : TagRepository {
        val rows = mutableMapOf<String, Tag>()
        var saveCount = 0
            private set

        override fun findById(id: PublicId): Tag? = rows[id.value]

        override fun findByInstanceAndSourceTagId(
            instanceId: PublicId,
            sourceTagId: String
        ): Tag? =
            rows.values.firstOrNull {
                it.origin == TagOrigin.EXTERNAL &&
                    it.instanceId == instanceId &&
                    it.sourceTagId == sourceTagId
            }

        override fun findPlatformByName(name: String): Tag? =
            rows.values.firstOrNull { it.origin == TagOrigin.PLATFORM && it.name == name }

        override fun save(tag: Tag): Tag {
            saveCount++
            rows[tag.id.value] = tag
            return tag
        }
    }

    // ------------------------------------------------------------------
    // ensurePlatform
    // ------------------------------------------------------------------

    @Test
    fun ensurePlatform_whenAbsent_createsPlatformTagAndSavesOnce() {
        val repo = FakeTagRepository()
        val uc = EnsureAndSaveTagUseCase(repo)

        val saved = uc.ensurePlatform(name = "Kotlin", normalizedName = "kotlin")

        assertNotNull(saved)
        assertTrue(saved.id.value.startsWith("tg_"))
        assertEquals(TagOrigin.PLATFORM, saved.origin)
        assertEquals("Kotlin", saved.name)
        assertNull(saved.sourceTagId)
        assertNull(saved.instanceId)
        assertNull(saved.sourceId)
        assertEquals(1, repo.saveCount)
        assertEquals(saved, repo.findPlatformByName("Kotlin"))
    }

    @Test
    fun ensurePlatform_whenPresent_returnsExistingWithoutSecondSave() {
        val repo = FakeTagRepository()
        val uc = EnsureAndSaveTagUseCase(repo)
        val first = uc.ensurePlatform("Kotlin", "kotlin")
        assertEquals(1, repo.saveCount)

        val again = uc.ensurePlatform(name = "Kotlin", normalizedName = "different")

        assertEquals(first, again)
        assertEquals(1, repo.saveCount, "save() must not be invoked for existing PLATFORM name")
    }

    // ------------------------------------------------------------------
    // ensureExternal
    // ------------------------------------------------------------------

    @Test
    fun ensureExternal_whenAbsent_createsExternalTagAndSavesOnce() {
        val repo = FakeTagRepository()
        val uc = EnsureAndSaveTagUseCase(repo)

        val saved = uc.ensureExternal(
            name = "Kotlin",
            normalizedName = "kotlin",
            sourceTagId = "st-1",
            instanceId = PublicId("inst-1"),
            sourceId = PublicId("src-1")
        )

        assertNotNull(saved)
        assertTrue(saved.id.value.startsWith("tg_"))
        assertEquals(TagOrigin.EXTERNAL, saved.origin)
        assertEquals("st-1", saved.sourceTagId)
        assertEquals(PublicId("inst-1"), saved.instanceId)
        assertEquals(PublicId("src-1"), saved.sourceId)
        assertEquals(1, repo.saveCount)
        assertEquals(
            saved,
            repo.findByInstanceAndSourceTagId(PublicId("inst-1"), "st-1")
        )
    }

    @Test
    fun ensureExternal_whenPresent_returnsExistingWithoutSecondSave() {
        val repo = FakeTagRepository()
        val uc = EnsureAndSaveTagUseCase(repo)
        val first = uc.ensureExternal("Kotlin", "kotlin", "st-1", PublicId("inst-1"), PublicId("src-1"))
        assertEquals(1, repo.saveCount)

        val again = uc.ensureExternal("Renamed", "renamed", "st-1", PublicId("inst-1"), PublicId("src-1"))

        assertEquals(first, again)
        assertEquals("Kotlin", again.name, "existing EXTERNAL tag data must not be overwritten")
        assertEquals(1, repo.saveCount)
    }

    // ------------------------------------------------------------------
    // PLATFORM vs EXTERNAL identity spaces stay independent
    // ------------------------------------------------------------------

    @Test
    fun platformAndExternalWithSameNameAreIndependent() {
        val repo = FakeTagRepository()
        val uc = EnsureAndSaveTagUseCase(repo)

        val p = uc.ensurePlatform("Kotlin", "kotlin")
        val e = uc.ensureExternal("Kotlin", "kotlin", "st-9", PublicId("inst-1"), PublicId("src-1"))

        assertNotEquals(p.id, e.id, "PLATFORM and EXTERNAL tags with the same name are distinct")
        assertNotEquals(p, e)
        assertEquals(2, repo.saveCount)
        assertEquals(1, repo.rows.values.count { it.origin == TagOrigin.PLATFORM })
        assertEquals(1, repo.rows.values.count { it.origin == TagOrigin.EXTERNAL })
    }
}
