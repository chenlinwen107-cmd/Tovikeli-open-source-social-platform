package com.contentplatform.backend.application.nav

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.NavCategory
import com.contentplatform.backend.repository.NavLink
import com.contentplatform.backend.repository.NavRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.util.UUID

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
class NavUseCase(
    private val nav: NavRepository,
    @Value("\${tovikeli.admin-username:admin}") private val adminUsername: String
) {
    fun categories() = nav.categories()
    fun popular(limit: Int) = nav.popular(limit)
    fun mine(userId: PublicId) = nav.mine(userId)

    fun addMine(userId: PublicId, title: String, url: String): NavLink {
        validateTitle(title); validateUrl(url)
        require(title.trim().length <= 40) { "website name is too long" }
        require(url.trim().length <= 300) { "url is too long" }
        return nav.addMine(PublicId(UUID.randomUUID().toString()), userId, title, url, "")
    }

    fun deleteMine(userId: PublicId, id: PublicId): Boolean = nav.deleteMine(id, userId)
    fun click(id: String): Boolean = nav.click(id)

    fun requireAdmin(username: String) {
        require(username == adminUsername) { "administrator access required" }
    }

    fun addCategory(username: String, name: String, icon: String): NavCategory {
        requireAdmin(username); require(name.trim().length in 1..20) { "invalid category name" }
        return nav.addCategory(PublicId(UUID.randomUUID().toString()), name, icon)
    }

    fun updateCategory(username: String, id: PublicId, name: String, icon: String): NavCategory? {
        requireAdmin(username); require(name.trim().length in 1..20) { "invalid category name" }
        return nav.updateCategory(id, name, icon)
    }

    fun deleteCategory(username: String, id: PublicId): Boolean { requireAdmin(username); return nav.deleteCategory(id) }

    fun addLink(username: String, categoryId: PublicId, title: String, url: String): NavLink {
        requireAdmin(username); validateTitle(title); validateUrl(url)
        return nav.addLink(PublicId(UUID.randomUUID().toString()), categoryId, title, url)
    }

    fun updateLink(username: String, id: PublicId, title: String, url: String): NavLink? {
        requireAdmin(username); validateTitle(title); validateUrl(url)
        return nav.updateLink(id, title, url)
    }

    fun deleteLink(username: String, id: PublicId): Boolean { requireAdmin(username); return nav.deleteLink(id) }

    private fun validateTitle(value: String) { require(value.trim().isNotEmpty()) { "website name is required" } }
    private fun validateUrl(value: String) {
        val u = URI(value.trim())
        require(u.scheme == "http" || u.scheme == "https") { "url must use http or https" }
        require(!u.host.isNullOrBlank()) { "invalid url" }
    }
}
