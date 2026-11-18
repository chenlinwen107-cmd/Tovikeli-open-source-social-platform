package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.repository.MediaRepository
import com.contentplatform.backend.repository.UserRepository
import com.contentplatform.backend.repository.PollRepository
import com.contentplatform.backend.repository.TopicRepository
import com.contentplatform.backend.application.poll.CreatePollDraft
import com.contentplatform.backend.application.poll.PollUseCase
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class PublishContentUseCase(
    private val userRepository: UserRepository,
    private val authorRepository: AuthorRepository,
    private val mediaRepository: MediaRepository,
    private val saveContentWithAssembliesUseCase: SaveContentWithAssembliesUseCase,
    private val pollUseCase: PollUseCase,
    private val pollRepository: PollRepository,
    private val topicRepository: TopicRepository
) {
    companion object {
        private val LOCAL_INSTANCE_ID = PublicId("inst_local")
        private val LOCAL_SOURCE_ID = PublicId("src_local")
    }

    fun publish(
        userId: PublicId,
        text: String,
        mediaIds: List<PublicId> = emptyList(),
        poll: CreatePollDraft? = null,
        location: String? = null,
        visibility: String? = null,
        anonymous: Boolean = false
    ): Content {
        val normalizedText = text.trim()
        val normalizedLocation = location?.trim()?.takeIf { it.isNotBlank() }
        val topicName = extractTopic(normalizedText)
        val topic = topicName?.let { topicRepository.ensure(it) }
        val requestedVisibility = visibility?.trim()?.lowercase() ?: "public"
        val contentVisibility = when (requestedVisibility) {
            "public", "anonymous" -> ContentVisibility.PUBLIC
            "private" -> ContentVisibility.PRIVATE
            else -> throw IllegalArgumentException("unsupported visibility")
        }
        require(normalizedText.isNotBlank() || mediaIds.isNotEmpty() || poll != null || normalizedLocation != null) {
            "text, media or poll or location must not be empty"
        }
        require(mediaIds.distinct().size == mediaIds.size) { "mediaIds must not contain duplicates" }

        val media = mediaIds.map { id ->
            mediaRepository.findById(id) ?: throw IllegalArgumentException("Media $id does not exist")
        }
        require(media.all { it.instanceId == LOCAL_INSTANCE_ID && it.status.name == "ACTIVE" }) {
            "media must belong to the local instance and be active"
        }
        require(media.all { it.metadata["uploadedByUserId"]?.toString() == userId.value }) {
            "media is not owned by caller"
        }

        val user = userRepository.findById(userId)
            ?: throw IllegalArgumentException("User $userId does not exist")

        val author = authorRepository.findByInstanceAndSourceAuthorId(LOCAL_INSTANCE_ID, user.id.value)
            ?: authorRepository.save(
                Author.createFromAdapter(
                    instanceId = LOCAL_INSTANCE_ID,
                    sourceAuthorId = user.id.value,
                    name = user.displayName ?: user.username,
                    username = user.username,
                    displayName = user.displayName,
                    url = null,
                    avatar = null,
                    bio = null,
                    status = user.status.name
                )
            )

        val sourceContentId = "${user.id.value}:${System.currentTimeMillis()}"
        val content = saveContentWithAssembliesUseCase.save(
            instanceId = LOCAL_INSTANCE_ID,
            sourceContentId = sourceContentId,
            authorId = author.id,
            sourceId = LOCAL_SOURCE_ID,
            text = normalizedText.ifBlank { null },
            language = null,
            visibility = contentVisibility,
            mediaIds = mediaIds,
            topicId = topic?.id,
            metadata = buildMap<String, Any?> {
                if (topic != null) { put("topicId", topic.id.value); put("topicName", topic.name) }
                if (normalizedLocation != null) put("location", normalizedLocation)
                if (requestedVisibility == "anonymous" || anonymous) put("anonymous", true)
            }
        )
        if (poll != null) {
            pollRepository.save(pollUseCase.create(content.id, poll))
        }
        return content
    }

    private fun extractTopic(text: String): String? {
        val match = Regex("#([^#\\r\\n]{1,50})#").find(text) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() }
    }
}
