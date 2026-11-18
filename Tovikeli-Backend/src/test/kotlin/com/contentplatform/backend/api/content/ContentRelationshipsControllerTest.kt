package com.contentplatform.backend.api.content

import com.contentplatform.backend.application.content.GetContentDetailUseCase
import com.contentplatform.backend.application.content.GetContentRelationshipsUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentRelationships
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.repository.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * MVC slice test for the Content Relationship endpoint (Stage 31).
 *
 * Tests GET /api/v1/contents/{contentId}/relationships.
 */
@WebMvcTest(controllers = [ContentController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class ContentRelationshipsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var browseContentUseCase: com.contentplatform.backend.application.content.BrowseContentUseCase

    @MockitoBean
    private lateinit var getContentDetailUseCase: GetContentDetailUseCase

    @MockitoBean
    private lateinit var getContentMediaUseCase: com.contentplatform.backend.application.content.GetContentMediaUseCase

    @MockitoBean
    private lateinit var getContentRelationshipsUseCase: GetContentRelationshipsUseCase

    @MockitoBean
    private lateinit var deleteContentUseCase: com.contentplatform.backend.application.content.DeleteContentUseCase

    @MockitoBean
    private lateinit var hideContentUseCase: com.contentplatform.backend.application.content.HideContentUseCase

    @MockitoBean
    private lateinit var restoreContentUseCase: com.contentplatform.backend.application.content.RestoreContentUseCase

    @MockitoBean
    private lateinit var userRepository: UserRepository

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private fun relatedContent(id: String): Content = Content.createFromAdapter(
        instanceIdValue = "inst_misskey_io",
        sourceContentId = "sc-$id",
        authorId = PublicId("au_author_1"),
        sourceId = PublicId("src_misskey"),
        text = "related $id",
        language = "en",
        visibility = ContentVisibility.PUBLIC,
        mediaIds = emptyList(),
        parentId = null,
        repostOfId = null,
        quoteOfId = null,
        externalCreatedAt = null,
        metadata = emptyMap()
    ).let { it.copy(id = PublicId(id)) }

    // ------------------------------------------------------------------
    // Readable current Content → 200
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_readableContent_returns200() {
        val contentId = "ct_1"
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId(contentId)))
            .thenReturn(ContentRelationships(parent = null, repostOf = null, quoteOf = null))

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parent").isEmpty)
            .andExpect(jsonPath("$.repostOf").isEmpty)
            .andExpect(jsonPath("$.quoteOf").isEmpty)
    }

    // ------------------------------------------------------------------
    // Missing current Content → 404
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_missingContent_returns404() {
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId("ct_missing")))
            .thenReturn(null)

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", "ct_missing"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // Inaccessible current Content → 404
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_inaccessibleContent_returns404() {
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId("ct_deleted")))
            .thenReturn(null)

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", "ct_deleted"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // All relationship fields null
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_allNull_returnsAllFieldsNull() {
        val contentId = "ct_1"
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId(contentId)))
            .thenReturn(ContentRelationships(parent = null, repostOf = null, quoteOf = null))

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parent").isEmpty)
            .andExpect(jsonPath("$.repostOf").isEmpty)
            .andExpect(jsonPath("$.quoteOf").isEmpty)
    }

    // ------------------------------------------------------------------
    // Partial relationships
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_parentOnly_returnsParentNotNull() {
        val contentId = "ct_1"
        val parent = relatedContent("ct_parent")
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId(contentId)))
            .thenReturn(ContentRelationships(parent = parent, repostOf = null, quoteOf = null))

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parent.id").value("ct_parent"))
            .andExpect(jsonPath("$.parent.text").value("related ct_parent"))
            .andExpect(jsonPath("$.repostOf").isEmpty)
            .andExpect(jsonPath("$.quoteOf").isEmpty)
    }

    @Test
    fun getRelationships_repostOfOnly_returnsRepostOfNotNull() {
        val contentId = "ct_1"
        val original = relatedContent("ct_original")
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId(contentId)))
            .thenReturn(ContentRelationships(parent = null, repostOf = original, quoteOf = null))

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parent").isEmpty)
            .andExpect(jsonPath("$.repostOf.id").value("ct_original"))
            .andExpect(jsonPath("$.quoteOf").isEmpty)
    }

    // ------------------------------------------------------------------
    // All relationships
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_allThree_returnsAllNotNull() {
        val contentId = "ct_1"
        val parent = relatedContent("ct_parent")
        val original = relatedContent("ct_original")
        val quoted = relatedContent("ct_quoted")
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId(contentId)))
            .thenReturn(ContentRelationships(parent = parent, repostOf = original, quoteOf = quoted))

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parent.id").value("ct_parent"))
            .andExpect(jsonPath("$.repostOf.id").value("ct_original"))
            .andExpect(jsonPath("$.quoteOf.id").value("ct_quoted"))
    }

    // ------------------------------------------------------------------
    // Inaccessible related Content becomes null
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_inaccessibleRelated_returnsNullField() {
        val contentId = "ct_1"
        val parent = relatedContent("ct_parent")
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId(contentId)))
            .thenReturn(ContentRelationships(parent = parent, repostOf = null, quoteOf = null))

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parent.id").value("ct_parent"))
            .andExpect(jsonPath("$.repostOf").isEmpty)
            .andExpect(jsonPath("$.quoteOf").isEmpty)
    }

    @Test
    fun getRelationships_nonPublicRelated_returnsNullField() {
        val contentId = "ct_1"
        val parent = relatedContent("ct_parent")
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId(contentId)))
            .thenReturn(ContentRelationships(parent = parent, repostOf = null, quoteOf = null))

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parent.id").value("ct_parent"))
            .andExpect(jsonPath("$.repostOf").isEmpty)
            .andExpect(jsonPath("$.quoteOf").isEmpty)
    }

    @Test
    fun getRelationships_allowIndexFalseRelated_returnsNullField() {
        val contentId = "ct_1"
        val parent = relatedContent("ct_parent")
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId(contentId)))
            .thenReturn(ContentRelationships(parent = parent, repostOf = null, quoteOf = null))

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parent.id").value("ct_parent"))
            .andExpect(jsonPath("$.repostOf").isEmpty)
            .andExpect(jsonPath("$.quoteOf").isEmpty)
    }

    @Test
    fun getRelationships_deletedRelated_returnsNullField() {
        val contentId = "ct_1"
        val parent = relatedContent("ct_parent")
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId(contentId)))
            .thenReturn(ContentRelationships(parent = parent, repostOf = null, quoteOf = null))

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parent.id").value("ct_parent"))
            .andExpect(jsonPath("$.repostOf").isEmpty)
            .andExpect(jsonPath("$.quoteOf").isEmpty)
    }

    @Test
    fun getRelationships_hiddenRelated_returnsNullField() {
        val contentId = "ct_1"
        val parent = relatedContent("ct_parent")
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId(contentId)))
            .thenReturn(ContentRelationships(parent = parent, repostOf = null, quoteOf = null))

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parent.id").value("ct_parent"))
            .andExpect(jsonPath("$.repostOf").isEmpty)
            .andExpect(jsonPath("$.quoteOf").isEmpty)
    }

    // ------------------------------------------------------------------
    // JSON field names and null behavior
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_jsonFieldNames_areCorrect() {
        val contentId = "ct_1"
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId(contentId)))
            .thenReturn(ContentRelationships(parent = null, repostOf = null, quoteOf = null))

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parent").isEmpty)
            .andExpect(jsonPath("$.repostOf").isEmpty)
            .andExpect(jsonPath("$.quoteOf").isEmpty)
    }

    @Test
    fun getRelationships_relatedContentFieldNames_areCorrect() {
        val contentId = "ct_1"
        val parent = relatedContent("ct_parent")
        Mockito.`when`(getContentRelationshipsUseCase.getRelationships(PublicId(contentId)))
            .thenReturn(ContentRelationships(parent = parent, repostOf = null, quoteOf = null))

        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parent.id").value("ct_parent"))
            .andExpect(jsonPath("$.parent.instanceId").value("inst_misskey_io"))
            .andExpect(jsonPath("$.parent.sourceContentId").value("sc-ct_parent"))
            .andExpect(jsonPath("$.parent.authorId").value("au_author_1"))
            .andExpect(jsonPath("$.parent.sourceId").value("src_misskey"))
            .andExpect(jsonPath("$.parent.text").value("related ct_parent"))
            .andExpect(jsonPath("$.parent.visibility").value("PUBLIC"))
            .andExpect(jsonPath("$.parent.status").value("ACTIVE"))
    }

    // ------------------------------------------------------------------
    // Invalid contentId path variable
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_blankContentId_returns404() {
        mockMvc.perform(get("/api/v1/contents/{contentId}/relationships", "   "))
            .andExpect(status().isNotFound)
    }
}
