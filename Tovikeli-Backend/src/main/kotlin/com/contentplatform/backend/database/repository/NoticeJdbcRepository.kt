package com.contentplatform.backend.database.repository

import com.contentplatform.backend.repository.NoticeRepository
import com.contentplatform.backend.repository.SiteNotice
import org.springframework.jdbc.core.JdbcTemplate

class NoticeJdbcRepository(private val jdbc:JdbcTemplate):NoticeRepository {
    private val mapper=org.springframework.jdbc.core.RowMapper<SiteNotice>{rs,_->SiteNotice(rs.getLong("id"),rs.getString("title"),rs.getString("body"),rs.getString("level"),rs.getString("link"),rs.getString("link_label"),rs.getBoolean("active"),rs.getBoolean("pinned"),rs.getLong("created_at"),rs.getLong("updated_at"))}
    private val cols="id,title,body,level,link,link_label,active,pinned,created_at,updated_at"
    override fun active()=jdbc.query("SELECT $cols FROM site_notice WHERE active=TRUE ORDER BY pinned DESC,created_at DESC,id DESC",mapper)
    override fun all()=jdbc.query("SELECT $cols FROM site_notice ORDER BY pinned DESC,created_at DESC,id DESC",mapper)
    override fun create(title:String,body:String,level:String,link:String,linkLabel:String,pinned:Boolean):SiteNotice{val now=System.currentTimeMillis();val id=jdbc.queryForObject("INSERT INTO site_notice(title,body,level,link,link_label,active,pinned,created_at,updated_at) VALUES(?,?,?,?,?,TRUE,?,?,?) RETURNING id",Long::class.java,title,body,level,link,linkLabel,pinned,now,now)!!;return all().first{it.id==id}}
    override fun update(id:Long,title:String?,body:String?,level:String?,link:String?,linkLabel:String?,active:Boolean?,pinned:Boolean?):SiteNotice?{val now=System.currentTimeMillis();val n=jdbc.update("UPDATE site_notice SET title=COALESCE(?,title),body=COALESCE(?,body),level=COALESCE(?,level),link=COALESCE(?,link),link_label=COALESCE(?,link_label),active=COALESCE(?,active),pinned=COALESCE(?,pinned),updated_at=? WHERE id=?",title,body,level,link,linkLabel,active,pinned,now,id);return if(n==0)null else all().firstOrNull{it.id==id}}
    override fun delete(id:Long)=jdbc.update("DELETE FROM site_notice WHERE id=?",id)>0
}
