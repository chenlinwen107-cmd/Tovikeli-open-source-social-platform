package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

class NavJdbcRepository(private val jdbc: JdbcTemplate) : NavRepository {

    override fun categories(): List<NavCategory> {
        val cats = jdbc.query(
            "SELECT id,name,icon FROM nav_category ORDER BY sort_order ASC,id ASC"
        ) { rs, _ ->
            NavCategory(rs.getString("id"), rs.getString("name"), rs.getString("icon"), emptyList())
        }
        if (cats.isEmpty()) return emptyList()
        val links = jdbc.query(
            "SELECT id,category_id,title,url,description,color,clicks FROM nav_link ORDER BY sort_order ASC,id ASC"
        ) { rs, _ -> mapLink(rs) }.groupBy { it.categoryId }
        return cats.map { it.copy(links = links[it.id].orEmpty()) }
    }

    override fun popular(limit: Int): List<NavLink> =
        jdbc.query(
            "SELECT id,category_id,title,url,description,color,clicks FROM nav_link ORDER BY clicks DESC,id ASC LIMIT ?",
            { rs, _ -> mapLink(rs) }, limit
        )

    override fun mine(userId: PublicId): List<NavLink> =
        jdbc.query(
            "SELECT id,NULL AS category_id,title,url,description,'#7c5cff' AS color,0::BIGINT AS clicks " +
                "FROM user_nav_link WHERE user_id=? ORDER BY created_at DESC,id DESC",
            { rs, _ -> mapLink(rs) }, userId.value
        )

    override fun addMine(id: PublicId, userId: PublicId, title: String, url: String, description: String): NavLink {
        jdbc.update(
            "INSERT INTO user_nav_link(id,user_id,title,url,description,created_at) VALUES(?,?,?,?,?,?)",
            id.value,userId.value,title.trim(),url.trim(),description.trim(),System.currentTimeMillis()
        )
        return mine(userId).first { it.id == id.value }
    }

    override fun deleteMine(id: PublicId, userId: PublicId): Boolean =
        jdbc.update("DELETE FROM user_nav_link WHERE id=? AND user_id=?", id.value, userId.value) > 0

    override fun click(id: String): Boolean {
        val global = jdbc.update("UPDATE nav_link SET clicks=clicks+1 WHERE id=?", id)
        if (global > 0) return true
        return jdbc.queryForObject(
            "SELECT count(*) FROM user_nav_link WHERE id=?",
            Long::class.java, id
        )?.let { it > 0 } ?: false
    }

    override fun addCategory(id: PublicId, name: String, icon: String): NavCategory {
        val order = (jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),-1)+1 FROM nav_category", Int::class.java) ?: 0)
        jdbc.update(
            "INSERT INTO nav_category(id,name,icon,sort_order,created_at) VALUES(?,?,?,?,?)",
            id.value,name.trim(),icon.trim().ifBlank { "compass" },order,System.currentTimeMillis()
        )
        return categories().first { it.id == id.value }
    }

    override fun updateCategory(id: PublicId, name: String, icon: String): NavCategory? {
        val n = jdbc.update("UPDATE nav_category SET name=?,icon=? WHERE id=?",name.trim(),icon.trim().ifBlank { "compass" },id.value)
        return if (n == 0) null else categories().first { it.id == id.value }
    }

    override fun deleteCategory(id: PublicId): Boolean =
        jdbc.update("DELETE FROM nav_category WHERE id=?",id.value) > 0

    override fun addLink(id: PublicId, categoryId: PublicId, title: String, url: String): NavLink {
        val order = (jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),-1)+1 FROM nav_link WHERE category_id=?", Int::class.java, categoryId.value) ?: 0)
        jdbc.update(
            "INSERT INTO nav_link(id,category_id,title,url,description,color,clicks,sort_order,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
            id.value,categoryId.value,title.trim(),url.trim(),"","#2b54f0",0L,order,System.currentTimeMillis()
        )
        return categories().flatMap { it.links }.first { it.id == id.value }
    }

    override fun updateLink(id: PublicId, title: String, url: String): NavLink? {
        val n = jdbc.update("UPDATE nav_link SET title=?,url=? WHERE id=?",title.trim(),url.trim(),id.value)
        return if (n == 0) null else jdbc.query(
            "SELECT id,category_id,title,url,description,color,clicks FROM nav_link WHERE id=?",
            { rs, _ -> mapLink(rs) }, id.value
        ).firstOrNull()
    }

    override fun deleteLink(id: PublicId): Boolean =
        jdbc.update("DELETE FROM nav_link WHERE id=?",id.value) > 0

    private fun mapLink(rs: java.sql.ResultSet) = NavLink(
        id=rs.getString("id"),
        categoryId=rs.getString("category_id"),
        title=rs.getString("title"),
        url=rs.getString("url"),
        description=rs.getString("description") ?: "",
        color=rs.getString("color") ?: "#2b54f0",
        clicks=rs.getLong("clicks")
    )
}
