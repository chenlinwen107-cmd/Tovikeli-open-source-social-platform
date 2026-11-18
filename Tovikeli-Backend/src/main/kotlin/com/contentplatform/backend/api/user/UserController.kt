package com.contentplatform.backend.api.user

import com.contentplatform.backend.application.user.CreateUserUseCase
import com.contentplatform.backend.application.user.UpdateUserProfileUseCase
import com.contentplatform.backend.api.authentication.CallerResolver
import jakarta.servlet.http.HttpServletRequest
import com.contentplatform.backend.application.user.GetUserUseCase
import com.contentplatform.backend.application.user.UsernameAlreadyExistsException
import com.contentplatform.backend.application.interaction.GetUserInteractionHistoryUseCase
import com.contentplatform.backend.application.interaction.InteractionQueryUserNotFoundException
import com.contentplatform.backend.api.user.InteractionResponse
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.FollowRepository
import com.contentplatform.backend.domain.common.PublicId
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Platform User API.
 *
 *   POST /api/v1/users          create a Platform User
 *   GET  /api/v1/users/{userId} read one Platform User detail
 *
 * Flow: HTTP -> this controller -> [CreateUserUseCase] / [GetUserUseCase] ->
 * UserRepository. The controller never touches a Repository directly and
 * contains no business logic.
 *
 * Create semantics (HTTP boundary only):
 *  - A JSON body is required. The request DTO is [CreateUserRequest]; missing
 *    / malformed JSON is answered by Spring MVC's standard handling with
 *    HTTP 400 (no custom error framework exists, matching every other endpoint
 *    in this project).
 *  - `username` is required and must be non-blank after trimming; a missing,
 *    empty or whitespace-only username is HTTP 400 with an empty body. The
 *    trimmed value is forwarded (mirroring the trim done by the search
 *    endpoints) and stored verbatim.
 *  - `displayName` is optional and is NOT identity: it is passed through
 *    unchanged (including JSON null) and may duplicate other users.
 *  - Success is HTTP 201 Created with the API [UserResponse] projection of the
 *    newly created ACTIVE user.
 *  - A duplicate username (any casing, e.g. "Alice" when "alice" exists) is
 *    answered with HTTP 409 and an empty body via
 *    [UsernameAlreadyExistsException]; the database UNIQUE index on
 *    LOWER(username) remains the final guarantee.
 *
 * Detail semantics: when the use case returns null the controller responds
 * HTTP 404 with an empty body; otherwise HTTP 200 with the API [UserResponse].
 * A blank path value is HTTP 400 with an empty body (mirroring the Tag API).
 *
 * PublicId: the raw path variable is wrapped by the existing [PublicId] value
 * object (its only rule: non-blank). No new parser and no prefix contract is
 * introduced at the API layer.
 *
 * Wiring: conditional on a configured datasource, mirroring the Stage 9/10B
 * persistence-backed use cases, so the no-datasource context keeps working.
 */
@RestController
@RequestMapping("/api/v1/users")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class UserController(
    private val createUserUseCase: CreateUserUseCase,
    private val getUserUseCase: GetUserUseCase,
    private val updateUserProfileUseCase: UpdateUserProfileUseCase,
    private val authorRepository: AuthorRepository,
    private val contentRepository: ContentRepository,
    private val followRepository: FollowRepository,
    private val interactionHistoryUseCase: GetUserInteractionHistoryUseCase
) {

    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<UserResponse> {
        val username = request.username?.trim().orEmpty()
        val password = request.password.orEmpty()

        if (username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().build()
        }

        return try {
            val user = createUserUseCase.create(
                username = username,
                password = password,
                displayName = request.displayName
            )
            ResponseEntity.status(201).body(UserResponse.from(user))
        } catch (e: UsernameAlreadyExistsException) {
            ResponseEntity.status(409).build()
        }
    }

    @PutMapping("/me/profile")
    fun updateProfile(
        @RequestBody request: UpdateProfileRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Map<String, UserResponse>> {
        val caller = CallerResolver.resolve(servletRequest) ?: return ResponseEntity.status(401).build()
        val user = updateUserProfileUseCase.update(caller.userId, request)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("user" to UserResponse.from(user)))
    }

    @GetMapping("/search")
    fun searchUsers(
        @org.springframework.web.bind.annotation.RequestParam(name = "q", required = false) q: String?,
        @org.springframework.web.bind.annotation.RequestParam(name = "limit", defaultValue = "20") limit: Int
    ): ResponseEntity<List<UserResponse>> {
        val query = q?.trim().orEmpty()
        if (query.isBlank()) return ResponseEntity.ok(emptyList())
        val safeLimit = limit.coerceIn(1, 50)
        return ResponseEntity.ok(getUserUseCase.search(query, safeLimit).map(UserResponse::from))
    }

    @GetMapping("/by-username/{username}")
    fun getUserByUsername(@PathVariable("username") username: String): ResponseEntity<UserResponse> {
        if (username.isBlank()) return ResponseEntity.badRequest().build()
        val user = getUserUseCase.getByUsername(username)
            ?: return ResponseEntity.notFound().build<UserResponse>()
        return ResponseEntity.ok(UserResponse.from(user))
    }

    @GetMapping("/me/bookmarks")
    fun getMyBookmarks(
        @org.springframework.web.bind.annotation.RequestParam(name = "limit", defaultValue = "20") limit: Int,
        @org.springframework.web.bind.annotation.RequestParam(name = "offset", defaultValue = "0") offset: Int,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Map<String, Any>> {
        val caller = CallerResolver.require(servletRequest)
        if (limit !in 1..100 || offset < 0) return ResponseEntity.badRequest().build()
        val items = interactionHistoryUseCase.getBookmarks(caller.userId, limit, offset)
        return ResponseEntity.ok(mapOf(
            "posts" to items.map { item -> InteractionResponse.from(item, item.content?.let { authorRepository.findById(it.authorId) }) },
            "hasMore" to (items.size == limit)
        ))
    }

    @GetMapping("/{userId}/stats")
    fun getUserStats(@PathVariable("userId") userId: String): ResponseEntity<Map<String, Long>> {
        if (userId.isBlank()) return ResponseEntity.badRequest().build()
        val id = PublicId(userId)
        if (getUserUseCase.getById(id) == null) return ResponseEntity.notFound().build()
        val author = authorRepository.findByInstanceAndSourceAuthorId(PublicId("inst_local"), userId)
        val postCount = author?.let { contentRepository.countActiveByAuthorId(it.id) } ?: 0L
        return ResponseEntity.ok(mapOf(
            "postCount" to postCount,
            "following" to followRepository.countFollowing(id),
            "followers" to followRepository.countFollowers(id)
        ))
    }

    @GetMapping("/{userId}")
    fun getUser(@PathVariable("userId") userId: String): ResponseEntity<UserResponse> {
        if (userId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val user = getUserUseCase.getById(PublicId(userId))
            ?: return ResponseEntity.notFound().build<UserResponse>()
        return ResponseEntity.ok(UserResponse.from(user))
    }
}
