package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentLifecycleResult
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.repository.ContentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class DeleteContentUseCaseTest {

    @Test
    fun `delete active content returns SUCCESS`() {
        val content = content(ContentStatus.ACTIVE)
        val repository = FakeContentRepository(content)
        val useCase = DeleteContentUseCase(repository, FakeAuthorRepository(), transactionTemplate())

        val result = useCase.delete(content.id, PublicId("caller_user"))

        assertEquals(ContentLifecycleResult.SUCCESS, result)
        assertEquals(1, repository.updateStatusCount)
        assertEquals(ContentStatus.DELETED, repository.lastUpdatedStatus)
    }

    @Test
    fun `delete missing content returns NOT_FOUND`() {
        val repository = FakeContentRepository(null)
        val useCase = DeleteContentUseCase(repository, FakeAuthorRepository(), transactionTemplate())

        val result = useCase.delete(PublicId("ct_not_exist"), PublicId("caller_user"))

        assertEquals(ContentLifecycleResult.NOT_FOUND, result)
        assertEquals(0, repository.updateStatusCount)
    }

    @Test
    fun `delete already deleted content returns INVALID_TRANSITION`() {
        val content = content(ContentStatus.DELETED)
        val repository = FakeContentRepository(content)
        val useCase = DeleteContentUseCase(repository, FakeAuthorRepository(), transactionTemplate())

        val result = useCase.delete(content.id, PublicId("caller_user"))

        assertEquals(ContentLifecycleResult.INVALID_TRANSITION, result)
        assertEquals(0, repository.updateStatusCount)
    }

    @Test
    fun `delete hidden content returns INVALID_TRANSITION`() {
        val content = content(ContentStatus.HIDDEN)
        val repository = FakeContentRepository(content)
        val useCase = DeleteContentUseCase(repository, FakeAuthorRepository(), transactionTemplate())

        val result = useCase.delete(content.id, PublicId("caller_user"))

        assertEquals(ContentLifecycleResult.INVALID_TRANSITION, result)
        assertEquals(0, repository.updateStatusCount)
    }

    @Test
    fun `delete unavailable content returns INVALID_TRANSITION`() {
        val content = content(ContentStatus.UNAVAILABLE)
        val repository = FakeContentRepository(content)
        val useCase = DeleteContentUseCase(repository, FakeAuthorRepository(), transactionTemplate())

        val result = useCase.delete(content.id, PublicId("caller_user"))

        assertEquals(ContentLifecycleResult.INVALID_TRANSITION, result)
        assertEquals(0, repository.updateStatusCount)
    }

    private class FakeAuthorRepository : AuthorRepository {
        private val author = Author(
            id = PublicId("au_author_1"),
            instanceId = PublicId("inst_local"),
            sourceAuthorId = "caller_user",
            name = "Caller",
            username = "caller",
            displayName = null,
            url = null,
            avatar = null,
            bio = null,
            status = null
        )
        override fun findById(id: PublicId): Author? = author.takeIf { it.id == id }
        override fun findByInstanceAndSourceAuthorId(instanceId: PublicId, sourceAuthorId: String): Author? =
            author.takeIf { it.instanceId == instanceId && it.sourceAuthorId == sourceAuthorId }
        override fun save(author: Author): Author = author
    }
    private fun content(status: ContentStatus): Content = Content.createFromAdapter(
        instanceIdValue = "inst_misskey_io",
        sourceContentId = "sc-1",
        authorId = PublicId("au_author_1"),
        sourceId = PublicId("src_misskey"),
        text = "test content",
        language = "en",
        visibility = ContentVisibility.PUBLIC,
        mediaIds = emptyList(),
        parentId = null,
        repostOfId = null,
        quoteOfId = null,
        externalCreatedAt = null,
        metadata = emptyMap()
    ).let { it.copy(status = status) }

    private fun transactionTemplate(): TransactionTemplate =
        TransactionTemplate(SimpleTransactionManager())

    private class FakeContentRepository(
        private val content: Content?
    ) : ContentRepository {

        var updateStatusCount = 0
            private set

        var lastUpdatedStatus: ContentStatus? = null
            private set

        override fun findById(id: PublicId): Content? = content?.takeIf { it.id == id }

        override fun findByInstanceAndSourceContentId(instanceId: PublicId, sourceContentId: String): Content? = null

        override fun save(content: Content): Content = content

        override fun updateStatus(id: PublicId, newStatus: ContentStatus): Boolean {
            updateStatusCount++
            lastUpdatedStatus = newStatus
            return true
        }
    }

    private class SimpleTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: org.springframework.transaction.TransactionDefinition) {}
        override fun doCommit(status: org.springframework.transaction.support.DefaultTransactionStatus) {}
        override fun doRollback(status: org.springframework.transaction.support.DefaultTransactionStatus) {}
    }
}
