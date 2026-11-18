package com.contentplatform.backend.application.column

import com.contentplatform.backend.application.content.DeleteContentUseCase
import com.contentplatform.backend.application.content.PublishContentUseCase
import com.contentplatform.backend.application.interaction.CreateInteractionUseCase
import com.contentplatform.backend.application.interaction.DeleteInteractionUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.interaction.InteractionType
import com.contentplatform.backend.repository.ColumnArticle
import com.contentplatform.backend.repository.ColumnRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class ColumnUseCase(
    private val columns: ColumnRepository,
    private val publish: PublishContentUseCase,
    private val deleteContent: DeleteContentUseCase,
    private val createLike: CreateInteractionUseCase,
    private val deleteLike: DeleteInteractionUseCase
) {
    companion object {
        val CATEGORIES = setOf("综合", "技术", "设计", "产品", "生活", "观点")
    }

    fun list(category: String?, sort: String, userId: PublicId?, limit: Int, offset: Int) =
        columns.list(category, sort, userId, limit, offset)

    fun detail(id: PublicId, userId: PublicId?): Pair<ColumnArticle, List<ColumnArticle>>? =
        columns.findById(id, userId)?.let { it to columns.listRelated(id, it.category, userId, 6) }

    fun trending(userId: PublicId?, limit: Int) = columns.listTrending(userId, limit)

    fun categories() = columns.categoryCounts()

    fun create(userId: PublicId, title: String, category: String, cover: String, summary: String, content: String): ColumnArticle {
        require(title.trim().length in 2..60) { "invalid title" }
        require(content.trim().length in 10..100000) { "invalid content" }
        require(category in CATEGORIES) { "invalid category" }
        require(cover.length <= 2000 && summary.length <= 120) { "invalid metadata" }
        val normalizedSummary = summary.trim().ifBlank { content.trim().replace(Regex("\\s+"), " ").take(120) }
        val saved = publish.publish(userId, content.trim())
        return columns.create(saved.id, title, category, cover, normalizedSummary, saved.createdAt)
    }

    fun like(userId: PublicId, id: PublicId): ColumnArticle? {
        val article = columns.findById(id, userId) ?: return null
        if (article.liked) deleteLike.delete(userId, id, InteractionType.LIKE)
        else createLike.create(userId, id, InteractionType.LIKE)
        return columns.findById(id, userId)
    }

    fun remove(userId: PublicId, id: PublicId): Boolean {
        val article = columns.findById(id, userId) ?: return false
        if (article.author.id != userId.value) throw SecurityException("forbidden")
        deleteContent.delete(id, userId)
        return columns.delete(id)
    }
}
