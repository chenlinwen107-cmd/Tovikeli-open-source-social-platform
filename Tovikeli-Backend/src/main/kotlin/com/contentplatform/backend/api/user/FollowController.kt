package com.contentplatform.backend.api.user

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.follow.FollowUseCase
import com.contentplatform.backend.domain.common.PublicId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class FollowUserResponse(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatar: String?,
    val bio: String?,
    val gender: String?,
    val location: String?,
    val cover: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isFollowing: Boolean,
)

@RestController
@RequestMapping("/api/v1/users")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class FollowController(private val followUseCase: FollowUseCase) {

    @GetMapping("/{userId}/follow")
    fun getFollow(@PathVariable userId: String, request: HttpServletRequest): ResponseEntity<Map<String, Boolean>> {
        val caller = CallerResolver.require(request)
        val target = try { PublicId(userId) } catch (_: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
        return ResponseEntity.ok(mapOf("following" to followUseCase.get(caller.userId, target)))
    }

    @PostMapping("/{userId}/follow")
    fun follow(@PathVariable userId: String, request: HttpServletRequest): ResponseEntity<Void> {
        val caller = CallerResolver.require(request)
        val target = try { PublicId(userId) } catch (_: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
        return try {
            followUseCase.follow(caller.userId, target)
            ResponseEntity.noContent().build()
        } catch (_: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @DeleteMapping("/{userId}/follow")
    fun unfollow(@PathVariable userId: String, request: HttpServletRequest): ResponseEntity<Void> {
        val caller = CallerResolver.require(request)
        val target = try { PublicId(userId) } catch (_: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
        followUseCase.unfollow(caller.userId, target)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{userId}/following")
    fun following(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "50") limit: Int,
        request: HttpServletRequest,
    ): ResponseEntity<List<FollowUserResponse>> =
        listUsers(userId, limit, request) { id, safeLimit -> followUseCase.listFollowing(id, safeLimit) }

    @GetMapping("/{userId}/followers")
    fun followers(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "50") limit: Int,
        request: HttpServletRequest,
    ): ResponseEntity<List<FollowUserResponse>> =
        listUsers(userId, limit, request) { id, safeLimit -> followUseCase.listFollowers(id, safeLimit) }

    private fun listUsers(
        userId: String,
        limit: Int,
        request: HttpServletRequest,
        loader: (PublicId, Int) -> List<com.contentplatform.backend.domain.user.User>,
    ): ResponseEntity<List<FollowUserResponse>> {
        val caller = CallerResolver.require(request)
        val target = try { PublicId(userId) } catch (_: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
        return try {
            val users = loader(target, limit)
            ResponseEntity.ok(users.map { user ->
                FollowUserResponse(
                    id = user.id.value,
                    username = user.username,
                    displayName = user.displayName,
                    avatar = user.avatar,
                    bio = user.bio,
                    gender = user.gender,
                    location = user.location,
                    cover = user.cover,
                    status = user.status.name,
                    createdAt = user.createdAt,
                    updatedAt = user.updatedAt,
                    isFollowing = followUseCase.get(caller.userId, user.id),
                )
            })
        } catch (_: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }
}
