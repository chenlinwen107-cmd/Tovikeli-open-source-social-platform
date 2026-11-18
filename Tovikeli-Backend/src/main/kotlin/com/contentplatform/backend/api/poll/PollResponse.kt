package com.contentplatform.backend.api.poll

import com.contentplatform.backend.domain.poll.PollResult
import java.time.Instant

data class PollOptionResponse(val id: String, val text: String, val votes: Long)

data class PollResponse(
    val id: String,
    val contentId: String,
    val multi: Boolean,
    val deadline: String?,
    val closed: Boolean,
    val totalVotes: Long,
    val voted: Boolean,
    val myVotes: List<String>,
    val options: List<PollOptionResponse>
) {
    companion object {
        fun from(result: PollResult): PollResponse =
            PollResponse(
                id = result.poll.id.value,
                contentId = result.poll.contentId.value,
                multi = result.poll.multi,
                deadline = result.poll.expiresAt?.let(Instant::ofEpochMilli)?.toString(),
                closed = result.closed,
                totalVotes = result.totalVotes,
                voted = result.myVotes.isNotEmpty(),
                myVotes = result.myVotes.toList(),
                options = result.poll.options.map {
                    PollOptionResponse(it.id.value, it.text, result.votesByOptionId[it.id.value] ?: 0L)
                }
            )
    }
}
