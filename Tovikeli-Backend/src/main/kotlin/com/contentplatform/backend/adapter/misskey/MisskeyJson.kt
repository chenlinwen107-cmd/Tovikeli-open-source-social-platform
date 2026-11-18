package com.contentplatform.backend.adapter.misskey

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * Jackson binding helper for Misskey JSON responses.
 *
 * PRIVATE to the Misskey adapter package. Unknown properties are ignored so
 * that future Misskey fields do not break parsing.
 *
 * The Kotlin module is registered so that Kotlin data-class defaults and
 * nullability are honoured: a constructor parameter with a default is filled
 * with that default when the property is absent, instead of being passed as
 * `null` and tripping the non-null parameter check. This is what makes the
 * adapter tolerant of fields Misskey may legitimately omit.
 */
internal object MisskeyJson {

    val mapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    fun parseNotes(body: String): List<MisskeyNote> =
        mapper.readValue(body, object : tools.jackson.core.type.TypeReference<List<MisskeyNote>>() {})
}
