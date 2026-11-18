package com.contentplatform.backend.adapter.mastodon

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * Jackson binding helper for Mastodon JSON responses.
 *
 * PRIVATE to the Mastodon adapter package. Unknown properties are ignored so
 * that future Mastodon fields do not break parsing.
 *
 * The Kotlin module is registered so that Kotlin data-class defaults and
 * nullability are honoured: a constructor parameter with a default is filled
 * with that default when the property is absent, instead of being passed as
 * `null` and tripping the non-null parameter check. This is what makes the
 * adapter tolerant of the fields Mastodon may legitimately omit (and of the
 * Mastodon 4.4+ `quote` entity that has no `id` of its own).
 */
internal object MastodonJson {

    val mapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    fun parseStatuses(body: String): List<MastodonStatus> =
        mapper.readValue(body, object : tools.jackson.core.type.TypeReference<List<MastodonStatus>>() {})
}
