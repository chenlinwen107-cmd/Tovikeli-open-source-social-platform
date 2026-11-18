package com.contentplatform.backend.application.poll

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.poll.Poll
import com.contentplatform.backend.domain.poll.PollResult
import com.contentplatform.backend.repository.PollRepository
import com.contentplatform.backend.repository.UserRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

data class CreatePollDraft(val options: List<String>, val multi: Boolean, val days: Int)

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class PollUseCase(
    private val pollRepository: PollRepository,
    private val userRepository: UserRepository,
    private val transactionTemplate: TransactionTemplate
) {
    fun create(contentId: PublicId, draft: CreatePollDraft): Poll {
        val options = draft.options.map { it.trim() }.filter { it.isNotBlank() }
        require(options.size in 2..6)
        require(options.distinct().size == options.size)
        require(options.all { it.length <= 60 })
        require(draft.days in setOf(0, 1, 3, 7))
        val expiresAt = if (draft.days == 0) null else System.currentTimeMillis() + draft.days * 86_400_000L
        return Poll.create(contentId, options, draft.multi, expiresAt)
    }

    fun batch(contentIds: List<PublicId>, userId: PublicId): Map<String, PollResult> {
        userRepository.findById(userId) ?: throw IllegalArgumentException("user does not exist")
        return pollRepository.findByContentIds(contentIds, userId)
    }

    fun vote(contentId: PublicId, userId: PublicId, optionIds: List<PublicId>): PollResult {
        userRepository.findById(userId) ?: throw IllegalArgumentException("user does not exist")
        return transactionTemplate.execute { pollRepository.addVotes(contentId, userId, optionIds) }
            ?: throw IllegalStateException("transaction returned no result")
    }
}
