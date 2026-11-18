package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

interface CommentLikeRepository {
    fun exists(userId: PublicId, commentId: PublicId): Boolean
    fun countByCommentId(commentId: PublicId): Long
    fun save(userId: PublicId, commentId: PublicId): Boolean
    fun delete(userId: PublicId, commentId: PublicId): Boolean
}
