package com.contentplatform.backend.api.notification

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.notification.NotificationUseCase
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val notifications:NotificationUseCase) {
    @GetMapping fun list(@RequestParam(defaultValue="50") limit:Int,req:HttpServletRequest):ResponseEntity<Any> = try {
        require(limit in 1..100)
        ResponseEntity.ok().header("Cache-Control","no-store, no-cache, must-revalidate").header("Pragma","no-cache").body(mapOf("notifications" to notifications.list(CallerResolver.require(req).userId,limit)))
    } catch(e:IllegalArgumentException){ResponseEntity.badRequest().body(mapOf("error" to (e.message?:"invalid request")))}
    @PostMapping("/read") fun markAll(req:HttpServletRequest):ResponseEntity<Any>{notifications.markAllRead(CallerResolver.require(req).userId);return ResponseEntity.ok(mapOf("ok" to true))}
    @PostMapping("/{id}/read") fun mark(@PathVariable id:Long,req:HttpServletRequest):ResponseEntity<Any>{notifications.markRead(CallerResolver.require(req).userId,id);return ResponseEntity.ok(mapOf("ok" to true))}
}
