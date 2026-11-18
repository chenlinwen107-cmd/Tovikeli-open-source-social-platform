package com.contentplatform.backend.database.support

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * Minimal JSON (de)serialization support for JSONB `metadata` columns.
 *
 * Uses the Jackson 3 (tools.jackson) mapper already provided by the project's
 * jackson-module-kotlin dependency. Internal to the database layer.
 */
object JsonSupport {

    private val mapper: ObjectMapper = ObjectMapper()

    /** Serializes a metadata map to a JSON string (never null; empty map -> "{}"). */
    fun toJson(metadata: Map<String, Any?>): String = mapper.writeValueAsString(metadata)

    /** Deserializes a JSON string back into a metadata map. */
    fun fromJson(json: String): Map<String, Any?> =
        mapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})
}
