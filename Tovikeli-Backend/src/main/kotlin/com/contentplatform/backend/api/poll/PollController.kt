package com.contentplatform.backend.api.poll

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.poll.PollUseCase
import com.contentplatform.backend.domain.common.PublicId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class VotePollRequest(val optionIds: List<String> = emptyList())

@RestController
@RequestMapping("/api/v1/polls")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class PollController(private val pollUseCase: PollUseCase) {
    @GetMapping("/batch")
    fun batch(
        @RequestParam contentIds: String,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Map<String, PollResponse>> {
        val caller = CallerResolver.require(servletRequest)
        val ids = contentIds.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty() || ids.size > 100) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(
            pollUseCase.batch(ids.map(::PublicId), caller.userId).mapValues { PollResponse.from(it.value) }
        )
    }

    @PostMapping("/content/{contentId}/vote")
    fun vote(
        @PathVariable contentId: String,
        @RequestBody request: VotePollRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<PollResponse> {
        val caller = CallerResolver.require(servletRequest)
        val ids = request.optionIds.map(::PublicId).distinct()
        if (ids.isEmpty() || ids.size > 6) return ResponseEntity.badRequest().build()
        return try {
            ResponseEntity.ok(PollResponse.from(pollUseCase.vote(PublicId(contentId), caller.userId, ids)))
        } catch (_: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }
}
