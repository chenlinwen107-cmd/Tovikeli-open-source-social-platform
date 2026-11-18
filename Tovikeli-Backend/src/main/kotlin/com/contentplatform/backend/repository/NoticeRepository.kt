package com.contentplatform.backend.repository

data class SiteNotice(val id:Long,val title:String,val body:String,val level:String,val link:String,val linkLabel:String,val active:Boolean,val pinned:Boolean,val createdAt:Long,val updatedAt:Long)

interface NoticeRepository {
    fun active():List<SiteNotice>
    fun all():List<SiteNotice>
    fun create(title:String,body:String,level:String,link:String,linkLabel:String,pinned:Boolean):SiteNotice
    fun update(id:Long,title:String?,body:String?,level:String?,link:String?,linkLabel:String?,active:Boolean?,pinned:Boolean?):SiteNotice?
    fun delete(id:Long):Boolean
}
