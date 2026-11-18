package com.contentplatform.backend.api.flash

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.flash.FlashUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.FlashItem
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class FlashRequest(val title:String,val summary:String="",val category:String,val url:String="",val pinned:Boolean=false)

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
@RequestMapping("/api/v1/flash")
class FlashController(private val flash:FlashUseCase){
 @GetMapping fun list(@RequestParam(required=false) category:String?,@RequestParam(required=false) q:String?,@RequestParam(defaultValue="50") limit:Int):ResponseEntity<Any> = try { require(limit in 1..100); ResponseEntity.ok(mapOf("flash" to flash.list(category,q,limit).flash.map(::mapFlash))) } catch(e:IllegalArgumentException){ResponseEntity.badRequest().body(mapOf("error" to (e.message?:"invalid request")))}
 @PostMapping fun create(@RequestBody body:FlashRequest,req:HttpServletRequest):ResponseEntity<Any> = try { ResponseEntity.status(201).body(mapOf("flash" to mapFlash(flash.create(CallerResolver.require(req).username,body.title,body.summary,body.category,body.url,body.pinned)))) } catch(e:IllegalArgumentException){ResponseEntity.badRequest().body(mapOf("error" to (e.message?:"invalid request")))}
 @PutMapping("/{id}") fun update(@PathVariable id:String,@RequestBody body:FlashRequest,req:HttpServletRequest):ResponseEntity<Any> = try { flash.update(CallerResolver.require(req).username,PublicId(id),body.title,body.summary,body.category,body.url,body.pinned)?.let{ResponseEntity.ok(mapOf("flash" to mapFlash(it)))}?:ResponseEntity.notFound().build() } catch(e:IllegalArgumentException){ResponseEntity.badRequest().body(mapOf("error" to (e.message?:"invalid request")))}
 @DeleteMapping("/{id}") fun delete(@PathVariable id:String,req:HttpServletRequest):ResponseEntity<Void> = try { if(flash.delete(CallerResolver.require(req).username,PublicId(id))) ResponseEntity.noContent().build() else ResponseEntity.notFound().build() } catch(e:IllegalArgumentException){ResponseEntity.badRequest().build()}
 private fun mapFlash(f:FlashItem)=mapOf("id" to f.id,"title" to f.title,"summary" to f.summary,"category" to f.category,"url" to f.url,"pinned" to f.pinned,"createdAt" to f.createdAt,"updatedAt" to f.updatedAt)
}