package com.contentplatform.backend.api.history

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.history.BrowseHistoryUseCase
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
@RequestMapping("/api/v1/history")
class HistoryController(private val history: BrowseHistoryUseCase) {
    @GetMapping
    fun list(req: HttpServletRequest): ResponseEntity<Any> =
        ResponseEntity.ok(mapOf("items" to history.list(CallerResolver.require(req).userId).map {
            mapOf(
                "type" to it.type,
                "id" to it.targetId,
                "title" to it.title,
                "link" to it.link,
                "icon" to it.icon,
                "typeLabel" to it.typeLabel,
                "author" to mapOf("nickname" to it.authorNickname),
                "viewedAt" to it.viewedAt,
                "cover" to it.cover,
                "solved" to it.solved
            )
        }))

    @DeleteMapping
    fun clear(req: HttpServletRequest): ResponseEntity<Void> {
        history.clear(CallerResolver.require(req).userId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{type}/{id}")
    fun delete(@PathVariable type:String,@PathVariable id:String,req:HttpServletRequest): ResponseEntity<Void> {
        history.delete(CallerResolver.require(req).userId,type,id)
        return ResponseEntity.noContent().build()
    }
}
