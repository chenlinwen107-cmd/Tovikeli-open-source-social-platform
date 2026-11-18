package com.contentplatform.backend.api.nav

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.nav.NavUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.NavCategory
import com.contentplatform.backend.repository.NavLink
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class AddMineNavRequest(val title: String, val url: String)
data class NavCategoryRequest(val name: String, val icon: String = "compass")
data class NavLinkRequest(val categoryId: String, val title: String, val url: String)

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
@RequestMapping("/api/v1/nav")
class NavController(private val nav: NavUseCase) {

    @GetMapping
    fun categories(): ResponseEntity<Any> =
        ResponseEntity.ok(mapOf("categories" to nav.categories().map(::mapCategory)))

    @GetMapping("/popular")
    fun popular(@RequestParam(defaultValue="8") limit: Int): ResponseEntity<Any> {
        require(limit in 1..30)
        return ResponseEntity.ok(mapOf("links" to nav.popular(limit).map(::mapLink)))
    }

    @GetMapping("/mine")
    fun mine(req: HttpServletRequest): ResponseEntity<Any> =
        ResponseEntity.ok(mapOf("links" to nav.mine(CallerResolver.require(req).userId).map(::mapLink)))

    @PostMapping("/mine")
    fun addMine(@RequestBody body: AddMineNavRequest, req: HttpServletRequest): ResponseEntity<Any> =
        try {
            ResponseEntity.status(201).body(mapOf("link" to mapLink(nav.addMine(CallerResolver.require(req).userId,body.title,body.url))))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "invalid request")))
        }

    @DeleteMapping("/mine/{id}")
    fun deleteMine(@PathVariable id: String, req: HttpServletRequest): ResponseEntity<Void> =
        if (nav.deleteMine(CallerResolver.require(req).userId,PublicId(id))) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()

    @PostMapping("/{id}/click")
    fun click(@PathVariable id: String): ResponseEntity<Void> =
        if (nav.click(id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    @PostMapping("/categories")
    fun addCategory(@RequestBody body: NavCategoryRequest, req: HttpServletRequest): ResponseEntity<Any> =
        try { ResponseEntity.status(201).body(mapOf("category" to mapCategory(nav.addCategory(CallerResolver.require(req).username,body.name,body.icon)))) }
        catch (e: IllegalArgumentException) { ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "invalid request"))) }

    @PutMapping("/categories/{id}")
    fun updateCategory(@PathVariable id: String,@RequestBody body: NavCategoryRequest,req: HttpServletRequest): ResponseEntity<Any> =
        try { nav.updateCategory(CallerResolver.require(req).username,PublicId(id),body.name,body.icon)?.let { ResponseEntity.ok(mapOf("category" to mapCategory(it))) } ?: ResponseEntity.notFound().build() }
        catch (e: IllegalArgumentException) { ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "invalid request"))) }

    @DeleteMapping("/categories/{id}")
    fun deleteCategory(@PathVariable id: String,req: HttpServletRequest): ResponseEntity<Void> =
        try { if(nav.deleteCategory(CallerResolver.require(req).username,PublicId(id))) ResponseEntity.noContent().build() else ResponseEntity.notFound().build() }
        catch (e: IllegalArgumentException) { ResponseEntity.badRequest().build() }

    @PostMapping("/links")
    fun addLink(@RequestBody body: NavLinkRequest,req: HttpServletRequest): ResponseEntity<Any> =
        try { ResponseEntity.status(201).body(mapOf("link" to mapLink(nav.addLink(CallerResolver.require(req).username,PublicId(body.categoryId),body.title,body.url)))) }
        catch (e: IllegalArgumentException) { ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "invalid request"))) }

    @PutMapping("/links/{id}")
    fun updateLink(@PathVariable id: String,@RequestBody body: NavLinkRequest,req: HttpServletRequest): ResponseEntity<Any> =
        try { nav.updateLink(CallerResolver.require(req).username,PublicId(id),body.title,body.url)?.let { ResponseEntity.ok(mapOf("link" to mapLink(it))) } ?: ResponseEntity.notFound().build() }
        catch (e: IllegalArgumentException) { ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "invalid request"))) }

    @DeleteMapping("/links/{id}")
    fun deleteLink(@PathVariable id: String,req: HttpServletRequest): ResponseEntity<Void> =
        try { if(nav.deleteLink(CallerResolver.require(req).username,PublicId(id))) ResponseEntity.noContent().build() else ResponseEntity.notFound().build() }
        catch (e: IllegalArgumentException) { ResponseEntity.badRequest().build() }

    private fun mapCategory(c: NavCategory) = mapOf("id" to c.id,"name" to c.name,"icon" to c.icon,"links" to c.links.map(::mapLink))
    private fun mapLink(l: NavLink) = mapOf("id" to l.id,"categoryId" to l.categoryId,"title" to l.title,"url" to l.url,"description" to l.description,"color" to l.color,"clicks" to l.clicks)
}
