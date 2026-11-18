package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.poll.Poll
import com.contentplatform.backend.domain.poll.PollOption
import com.contentplatform.backend.domain.poll.PollResult
import com.contentplatform.backend.repository.PollRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate

class PollJdbcRepository(private val jdbcTemplate: JdbcTemplate) : PollRepository {
    override fun save(poll: Poll): Poll {
        jdbcTemplate.update(
            "INSERT INTO poll (id, content_id, multi, expires_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            poll.id.value, poll.contentId.value, poll.multi, poll.expiresAt, poll.createdAt, poll.updatedAt
        )
        poll.options.forEach {
            jdbcTemplate.update(
                "INSERT INTO poll_option (id, poll_id, text, position) VALUES (?, ?, ?, ?)",
                it.id.value, it.pollId.value, it.text, it.position
            )
        }
        return poll
    }

    override fun findByContentIds(contentIds: List<PublicId>, userId: PublicId?): Map<String, PollResult> {
        if (contentIds.isEmpty()) return emptyMap()
        val cp = contentIds.joinToString(",") { "?" }
        val rows = jdbcTemplate.query(
            "SELECT id, content_id, multi, expires_at, created_at, updated_at FROM poll WHERE content_id IN ($cp)",
            { rs, _ -> PollRow(
                rs.getString("id"), rs.getString("content_id"), rs.getBoolean("multi"),
                rs.getObject("expires_at") as Long?, rs.getLong("created_at"), rs.getLong("updated_at")
            ) },
            *contentIds.map { it.value }.toTypedArray()
        )
        if (rows.isEmpty()) return emptyMap()
        val pollIds = rows.map { it.id }
        val pp = pollIds.joinToString(",") { "?" }
        val options = jdbcTemplate.query(
            "SELECT id, poll_id, text, position FROM poll_option WHERE poll_id IN ($pp) ORDER BY poll_id, position",
            { rs, _ -> PollOption(PublicId(rs.getString("id")), PublicId(rs.getString("poll_id")), rs.getString("text"), rs.getInt("position")) },
            *pollIds.toTypedArray()
        ).groupBy { it.pollId.value }
        val counts = jdbcTemplate.query(
            "SELECT poll_id, option_id, COUNT(*) AS votes FROM poll_vote WHERE poll_id IN ($pp) GROUP BY poll_id, option_id",
            { rs, _ -> rs.getString("poll_id") to (rs.getString("option_id") to rs.getLong("votes")) },
            *pollIds.toTypedArray()
        ).groupBy({ it.first }, { it.second }).mapValues { it.value.toMap() }
        val mine = if (userId == null) emptyMap() else jdbcTemplate.query(
            "SELECT poll_id, option_id FROM poll_vote WHERE poll_id IN ($pp) AND user_id = ?",
            { rs, _ -> rs.getString("poll_id") to rs.getString("option_id") },
            *pollIds.toTypedArray(), userId.value
        ).groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }

        return rows.associate { row ->
            val poll = Poll(
                PublicId(row.id), PublicId(row.contentId), row.multi, row.expiresAt,
                options[row.id].orEmpty(), row.createdAt, row.updatedAt
            )
            val voteCounts = counts[row.id].orEmpty()
            row.contentId to PollResult(poll, voteCounts, voteCounts.values.sum(), mine[row.id].orEmpty())
        }
    }

    override fun addVotes(contentId: PublicId, userId: PublicId, optionIds: List<PublicId>): PollResult {
        val poll = findByContentIds(listOf(contentId), userId)[contentId.value]?.poll
            ?: throw IllegalArgumentException("poll does not exist")
        if (poll.expiresAt?.let { it <= System.currentTimeMillis() } == true) throw IllegalArgumentException("poll has ended")
        val ids = optionIds.distinct()
        require(ids.isNotEmpty() && ids.size <= 6)
        require(poll.multi || ids.size == 1)
        require(ids.all { candidate -> poll.options.any { it.id == candidate } })
        val existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM poll_vote WHERE poll_id = ? AND user_id = ?",
            Long::class.java, poll.id.value, userId.value
        ) ?: 0L
        require(existing == 0L) { "user has already voted" }
        try {
            ids.forEach {
                jdbcTemplate.update(
                    "INSERT INTO poll_vote (poll_id, option_id, user_id, created_at) VALUES (?, ?, ?, ?)",
                    poll.id.value, it.value, userId.value, System.currentTimeMillis()
                )
            }
        } catch (_: DuplicateKeyException) {
            throw IllegalArgumentException("user has already voted")
        }
        return findByContentIds(listOf(contentId), userId)[contentId.value]
            ?: throw IllegalStateException("poll disappeared after vote")
    }
    
    private data class PollRow(
        val id: String, val contentId: String, val multi: Boolean,
        val expiresAt: Long?, val createdAt: Long, val updatedAt: Long
    )
}
