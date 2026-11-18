package com.contentplatform.backend.api.event

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.event.EventUseCase
import com.contentplatform.backend.application.history.BrowseHistoryUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CreateEventRequest(val title:String,val cover:String="",val location:String="",val category:String,val startAt:Long,val endAt:Long,val capacity:Int=0,val fee:Double=0.0,val online:Boolean=false,val description:String="")

@RestController
@RequestMapping("/api/v1/events")
class EventController(private val events:EventUseCase, private val history: BrowseHistoryUseCase) {
    @GetMapping fun list(@RequestParam(required=false) filter:String?,@RequestParam(required=false) category:String?,@RequestParam(defaultValue="20") limit:Int,req:HttpServletRequest):ResponseEntity<Any> = try {
        require(limit in 1..100); val r=events.list(filter,category,CallerResolver.resolve(req)?.userId,limit)
        ResponseEntity.ok(mapOf("events" to r.events.map(::mapEvent),"categories" to r.categories,"counts" to mapOf("upcoming" to r.upcomingCount)))
    } catch(_:IllegalArgumentException){ResponseEntity.badRequest().build()}
    @GetMapping("/{id}") fun detail(@PathVariable id:String,req:HttpServletRequest):ResponseEntity<Any> =
        events.detail(PublicId(id),CallerResolver.resolve(req)?.userId)?.let{ d -> CallerResolver.resolve(req)?.userId?.let { u -> history.record(u, "event", id, d.event.title, "活动", "/event/$id", "calendar", d.event.organizer.nickname, d.event.cover) }; ResponseEntity.ok(mapOf("event" to mapEvent(d.event),"attendees" to d.attendees.map(::mapOrganizer)))} ?: ResponseEntity.notFound().build()
    @PostMapping fun create(@RequestBody body:CreateEventRequest,req:HttpServletRequest):ResponseEntity<Any> = try {
        ResponseEntity.status(201).body(mapOf("event" to mapEvent(events.create(CallerResolver.require(req).userId,body.title,body.cover,body.location,body.category,body.startAt,body.endAt,body.capacity,body.fee,body.online,body.description))))
    } catch(_:IllegalArgumentException){ResponseEntity.badRequest().build()}
    @PostMapping("/{id}/signup") fun signup(@PathVariable id:String,req:HttpServletRequest):ResponseEntity<Any> = try { ResponseEntity.ok(mapOf("event" to mapEvent(events.signup(CallerResolver.require(req).userId,PublicId(id))))) } catch(_:IllegalArgumentException){ResponseEntity.badRequest().build()}
    @PostMapping("/{id}/cancel") fun cancel(@PathVariable id:String,req:HttpServletRequest):ResponseEntity<Any> = try { ResponseEntity.ok(mapOf("event" to mapEvent(events.cancel(CallerResolver.require(req).userId,PublicId(id))))) } catch(_:IllegalArgumentException){ResponseEntity.badRequest().build()}
    @DeleteMapping("/{id}") fun delete(@PathVariable id:String,req:HttpServletRequest):ResponseEntity<Void> = try { if(events.delete(CallerResolver.require(req).userId,PublicId(id)))ResponseEntity.noContent().build() else ResponseEntity.notFound().build() } catch(_:SecurityException){ResponseEntity.status(403).build()} catch(_:IllegalArgumentException){ResponseEntity.badRequest().build()}
    private fun mapEvent(e:CommunityEvent)=mapOf("id" to e.id,"title" to e.title,"cover" to e.cover,"location" to e.location,"category" to e.category,"startAt" to e.startAt,"endAt" to e.endAt,"capacity" to e.capacity,"fee" to e.fee,"online" to e.online,"signupCount" to e.signupCount,"spotsLeft" to e.spotsLeft,"status" to e.status,"full" to e.full,"signed" to e.signed,"organizer" to mapOrganizer(e.organizer),"isOrganizer" to e.isOrganizer,"description" to e.description)
    private fun mapOrganizer(o:EventOrganizer)=mapOf("id" to o.id,"username" to o.username,"nickname" to o.nickname,"avatar" to o.avatar)
}