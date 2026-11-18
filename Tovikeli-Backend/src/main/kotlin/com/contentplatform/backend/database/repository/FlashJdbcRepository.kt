package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.FlashItem
import com.contentplatform.backend.repository.FlashListResult
import com.contentplatform.backend.repository.FlashRepository
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.ResultSet

class FlashJdbcRepository(private val jdbc: JdbcTemplate) : FlashRepository {
    override fun list(category: String?, query: String?, limit: Int): FlashListResult {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()
        if (!category.isNullOrBlank()) { where += "category = ?"; args += category }
        if (!query.isNullOrBlank()) { where += "(title ILIKE ? OR summary ILIKE ?)"; val q = "%" + query.trim() + "%"; args += q; args += q }
        val clause = if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")
        args += limit
        val rows = jdbc.query("SELECT id,title,summary,category,url,pinned,created_at,updated_at FROM flash $clause ORDER BY pinned DESC, created_at DESC, id DESC LIMIT ?", { rs, _ -> map(rs) }, *args.toTypedArray())
        return FlashListResult(rows)
    }
    override fun findById(id: PublicId): FlashItem? = jdbc.query("SELECT id,title,summary,category,url,pinned,created_at,updated_at FROM flash WHERE id=?", { rs, _ -> map(rs) }, id.value).firstOrNull()
    override fun create(id: PublicId, title: String, summary: String, category: String, url: String, pinned: Boolean, now: Long): FlashItem {
        jdbc.update("INSERT INTO flash(id,title,summary,category,url,pinned,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)", id.value,title.trim(),summary.trim(),category,url.trim(),pinned,now,now); return findById(id)!! }
    override fun update(id: PublicId, title: String, summary: String, category: String, url: String, pinned: Boolean, now: Long): FlashItem? {
        val changed=jdbc.update("UPDATE flash SET title=?,summary=?,category=?,url=?,pinned=?,updated_at=? WHERE id=?",title.trim(),summary.trim(),category,url.trim(),pinned,now,id.value); return if(changed==0)null else findById(id) }
    override fun delete(id: PublicId): Boolean = jdbc.update("DELETE FROM flash WHERE id=?",id.value)>0
    private fun map(rs: ResultSet)=FlashItem(rs.getString("id"),rs.getString("title"),rs.getString("summary"),rs.getString("category"),rs.getString("url"),rs.getBoolean("pinned"),rs.getLong("created_at"),rs.getLong("updated_at"))
}