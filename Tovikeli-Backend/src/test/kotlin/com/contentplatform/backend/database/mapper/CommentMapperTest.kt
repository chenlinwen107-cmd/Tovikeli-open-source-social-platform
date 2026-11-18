package com.contentplatform.backend.database.mapper

import com.contentplatform.backend.database.entity.CommentEntity
import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.comment.CommentStatus
import com.contentplatform.backend.domain.common.PublicId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommentMapperTest {

    @Test
    fun toEntity_mapsAllFieldsIncludingNullableParent() {
        val comment = Comment(
            id = PublicId("cmt_123"),
            userId = PublicId("usr_123"),
            contentId = PublicId("cnt_123"),
            parentCommentId = PublicId("cmt_parent"),
            text = "hello",
            status = CommentStatus.ACTIVE,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val entity = CommentMapper.toEntity(comment)

        assertEquals("cmt_123", entity.id)
        assertEquals("usr_123", entity.userId)
        assertEquals("cnt_123", entity.contentId)
        assertEquals("cmt_parent", entity.parentCommentId)
        assertEquals("hello", entity.text)
        assertEquals("ACTIVE", entity.status)
        assertEquals(1000L, entity.createdAt)
        assertEquals(2000L, entity.updatedAt)
    }

    @Test
    fun toEntity_preservesNullParent() {
        val comment = Comment(
            id = PublicId("cmt_123"),
            userId = PublicId("usr_123"),
            contentId = PublicId("cnt_123"),
            parentCommentId = null,
            text = "top level",
            status = CommentStatus.ACTIVE,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val entity = CommentMapper.toEntity(comment)

        assertNull(entity.parentCommentId)
    }

    @Test
    fun toDomain_mapsAllFieldsIncludingNullableParent() {
        val entity = CommentEntity(
            id = "cmt_123",
            userId = "usr_123",
            contentId = "cnt_123",
            parentCommentId = "cmt_parent",
            text = "reply",
            status = "DELETED",
            createdAt = 1000L,
            updatedAt = 3000L
        )

        val comment = CommentMapper.toDomain(entity)

        assertEquals(PublicId("cmt_123"), comment.id)
        assertEquals(PublicId("usr_123"), comment.userId)
        assertEquals(PublicId("cnt_123"), comment.contentId)
        assertEquals(PublicId("cmt_parent"), comment.parentCommentId)
        assertEquals("reply", comment.text)
        assertEquals(CommentStatus.DELETED, comment.status)
        assertEquals(1000L, comment.createdAt)
        assertEquals(3000L, comment.updatedAt)
    }

    @Test
    fun toDomain_preservesNullParent() {
        val entity = CommentEntity(
            id = "cmt_123",
            userId = "usr_123",
            contentId = "cnt_123",
            parentCommentId = null,
            text = "top level",
            status = "ACTIVE",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val comment = CommentMapper.toDomain(entity)

        assertNull(comment.parentCommentId)
    }
}
