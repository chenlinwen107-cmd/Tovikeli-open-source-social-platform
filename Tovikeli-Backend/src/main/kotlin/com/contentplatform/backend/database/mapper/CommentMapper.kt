package com.contentplatform.backend.database.mapper

import com.contentplatform.backend.database.entity.CommentEntity
import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.comment.CommentStatus
import com.contentplatform.backend.domain.common.PublicId

/**
 * Bidirectional mapper between Domain [Comment] and persistence
 * [CommentEntity].
 */
object CommentMapper {

    fun toEntity(comment: Comment): CommentEntity = CommentEntity(
        id = comment.id.value,
        userId = comment.userId.value,
        contentId = comment.contentId.value,
        parentCommentId = comment.parentCommentId?.value,
        text = comment.text,
        status = comment.status.name,
        createdAt = comment.createdAt,
        updatedAt = comment.updatedAt
    )

    fun toDomain(entity: CommentEntity): Comment = Comment(
        id = PublicId(entity.id),
        userId = PublicId(entity.userId),
        contentId = PublicId(entity.contentId),
        parentCommentId = entity.parentCommentId?.let(::PublicId),
        text = entity.text,
        status = CommentStatus.valueOf(entity.status),
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )
}
