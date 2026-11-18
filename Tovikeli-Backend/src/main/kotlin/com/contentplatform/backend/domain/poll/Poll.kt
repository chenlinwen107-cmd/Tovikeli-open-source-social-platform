package com.contentplatform.backend.domain.poll

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId

data class PollOption(
    val id: PublicId,
    val pollId: PublicId,
    val text: String,
    val position: Int
)

data class Poll(
    val id: PublicId,
    val contentId: PublicId,
    val multi: Boolean,
    val expiresAt: Long?,
    val options: List<PollOption>,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        fun create(contentId: PublicId, options: List<String>, multi: Boolean, expiresAt: Long?): Poll {
            require(options.size in 2..6)
            val now = System.currentTimeMillis()
            val pollId = PublicId("poll_" + IdentityHash.derive(contentId.value))
            return Poll(
                id = pollId,
                contentId = contentId,
                multi = multi,
                expiresAt = expiresAt,
                options = options.mapIndexed { index, text ->
                    PollOption(
                        PublicId("popt_" + IdentityHash.derive(contentId.value + ":" + index)),
                        pollId,
                        text.trim(),
                        index
                    )
                },
                createdAt = now,
                updatedAt = now
            )
        }
    }
}

data class PollResult(
    val poll: Poll,
    val votesByOptionId: Map<String, Long>,
    val totalVotes: Long,
    val myVotes: Set<String>
) {
    val closed: Boolean
        get() = poll.expiresAt?.let { it <= System.currentTimeMillis() } == true
}
