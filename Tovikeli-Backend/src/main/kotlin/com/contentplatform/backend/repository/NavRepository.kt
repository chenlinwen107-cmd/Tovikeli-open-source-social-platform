package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

data class NavLink(
    val id: String,
    val categoryId: String? = null,
    val title: String,
    val url: String,
    val description: String = "",
    val color: String = "#2b54f0",
    val clicks: Long = 0
)

data class NavCategory(
    val id: String,
    val name: String,
    val icon: String,
    val links: List<NavLink>
)

interface NavRepository {
    fun categories(): List<NavCategory>
    fun popular(limit: Int): List<NavLink>
    fun mine(userId: PublicId): List<NavLink>
    fun addMine(id: PublicId, userId: PublicId, title: String, url: String, description: String): NavLink
    fun deleteMine(id: PublicId, userId: PublicId): Boolean
    fun click(id: String): Boolean

    fun addCategory(id: PublicId, name: String, icon: String): NavCategory
    fun updateCategory(id: PublicId, name: String, icon: String): NavCategory?
    fun deleteCategory(id: PublicId): Boolean
    fun addLink(id: PublicId, categoryId: PublicId, title: String, url: String): NavLink
    fun updateLink(id: PublicId, title: String, url: String): NavLink?
    fun deleteLink(id: PublicId): Boolean
}
