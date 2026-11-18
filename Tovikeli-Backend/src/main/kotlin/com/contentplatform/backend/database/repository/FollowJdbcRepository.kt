package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.follow.Follow
import com.contentplatform.backend.repository.FollowRepository
import org.springframework.jdbc.core.JdbcTemplate

class FollowJdbcRepository(private val jdbcTemplate: JdbcTemplate) : FollowRepository {
    override fun exists(followerUserId: PublicId, followedUserId: PublicId): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM user_follow WHERE follower_user_id = ? AND followed_user_id = ?)",
            Boolean::class.java, followerUserId.value, followedUserId.value
        ) ?: false

    override fun save(follow: Follow): Follow {
        jdbcTemplate.update(
            "INSERT INTO user_follow (follower_user_id, followed_user_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
            follow.followerUserId.value, follow.followedUserId.value, follow.createdAt
        )
        return follow
    }

    override fun delete(followerUserId: PublicId, followedUserId: PublicId): Boolean =
        jdbcTemplate.update(
            "DELETE FROM user_follow WHERE follower_user_id = ? AND followed_user_id = ?",
            followerUserId.value, followedUserId.value
        ) > 0

    override fun countFollowing(userId: PublicId): Long = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM user_follow WHERE follower_user_id = ?", Long::class.java, userId.value
    ) ?: 0L

    override fun countFollowers(userId: PublicId): Long = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM user_follow WHERE followed_user_id = ?", Long::class.java, userId.value
    ) ?: 0L

    override fun findFollowedUserIds(followerUserId: PublicId, limit: Int): List<PublicId> {
        require(limit >= 0)
        if (limit == 0) return emptyList()
        return jdbcTemplate.query(
            """
            SELECT f.followed_user_id
            FROM user_follow f
            JOIN platform_user u ON u.id = f.followed_user_id
            WHERE f.follower_user_id = ? AND u.status = 'ACTIVE'
            ORDER BY f.created_at DESC, f.followed_user_id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> PublicId(rs.getString(1)) },
            followerUserId.value, limit
        )
    }

    override fun findFollowerUserIds(followedUserId: PublicId, limit: Int): List<PublicId> {
        require(limit >= 0)
        if (limit == 0) return emptyList()
        return jdbcTemplate.query(
            """
            SELECT f.follower_user_id
            FROM user_follow f
            JOIN platform_user u ON u.id = f.follower_user_id
            WHERE f.followed_user_id = ? AND u.status = 'ACTIVE'
            ORDER BY f.created_at DESC, f.follower_user_id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> PublicId(rs.getString(1)) },
            followedUserId.value, limit
        )
    }
}
