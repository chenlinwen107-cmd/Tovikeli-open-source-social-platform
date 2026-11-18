package com.contentplatform.backend.api.messaging

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.messaging.DirectMessagingUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.messaging.DirectMessage
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CreateConversationRequest(val userId: String)
data class SendDirectMessageRequest(val body: String?)

data class DirectUserResponse(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatar: String?
)

data class DirectMessageResponse(
    val id: String,
    val conversationId: String,
    val senderUserId: String,
    val body: String,
    val createdAt: Long,
    val readAt: Long?
) {
    companion object {
        fun from(message: DirectMessage) = DirectMessageResponse(
            message.id.value, message.conversationId.value, message.senderUserId.value,
            message.body, message.createdAt, message.readAt
        )
    }
}

data class DirectConversationResponse(
    val id: String,
    val user: DirectUserResponse,
    val lastMessage: DirectMessageResponse?,
    val unreadCount: Long,
    val updatedAt: Long
)

@RestController
@RequestMapping("/api/v1/messages")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class DirectMessageController(
    private val messaging: DirectMessagingUseCase
) {
    @GetMapping("/conversations")
    fun listConversations(
        @RequestParam(defaultValue = "50") limit: Int,
        request: HttpServletRequest
    ): ResponseEntity<List<DirectConversationResponse>> {
        val caller = CallerResolver.require(request)
        if (limit !in 1..100) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(
            messaging.listConversations(caller.userId, limit).map {
                DirectConversationResponse(
                    id = it.conversation.id.value,
                    user = DirectUserResponse(
                        id = it.otherUser.id.value,
                        username = it.otherUser.username,
                        displayName = it.otherUser.displayName,
                        avatar = it.otherUser.avatar
                    ),
                    lastMessage = it.lastMessage?.let(DirectMessageResponse::from),
                    unreadCount = it.unreadCount,
                    updatedAt = it.conversation.updatedAt
                )
            }
        )
    }

    @PostMapping("/conversations")
    fun createConversation(
        @RequestBody requestBody: CreateConversationRequest,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, String>> {
        val caller = CallerResolver.require(request)
        return try {
            val other = PublicId(requestBody.userId)
            val conversation = messaging.getOrCreateConversation(caller.userId, other)
            ResponseEntity.ok(mapOf("id" to conversation.id.value))
        } catch (_: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (_: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/conversations/{conversationId}/messages")
    fun listMessages(
        @PathVariable conversationId: String,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) beforeCreatedAt: Long?,
        request: HttpServletRequest
    ): ResponseEntity<List<DirectMessageResponse>> {
        val caller = CallerResolver.require(request)
        if (limit !in 1..100) return ResponseEntity.badRequest().build()
        return try {
            ResponseEntity.ok(
                messaging.listMessages(
                    caller.userId, PublicId(conversationId), limit, beforeCreatedAt
                ).map(DirectMessageResponse::from)
            )
        } catch (_: IllegalArgumentException) {
            ResponseEntity.status(403).build()
        }
    }

    @PostMapping("/conversations/{conversationId}/messages")
    fun sendMessage(
        @PathVariable conversationId: String,
        @RequestBody requestBody: SendDirectMessageRequest,
        request: HttpServletRequest
    ): ResponseEntity<DirectMessageResponse> {
        val caller = CallerResolver.require(request)
        return try {
            val message = messaging.sendMessage(
                caller.userId, PublicId(conversationId), requestBody.body.orEmpty()
            )
            ResponseEntity.ok(DirectMessageResponse.from(message))
        } catch (_: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (_: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/conversations/{conversationId}/read")
    fun markRead(
        @PathVariable conversationId: String,
        request: HttpServletRequest
    ): ResponseEntity<Void> {
        val caller = CallerResolver.require(request)
        return try {
            messaging.markRead(caller.userId, PublicId(conversationId))
            ResponseEntity.noContent().build()
        } catch (_: IllegalArgumentException) {
            ResponseEntity.status(403).build()
        } catch (_: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }
}
