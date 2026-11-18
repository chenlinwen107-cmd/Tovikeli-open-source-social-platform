package com.contentplatform.backend.adapter.misskey

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * JSON parsing tests for the Misskey adapter (P9-F).
 *
 * Offline fixtures only; no network. Verifies the parser tolerates unknown
 * properties, missing optional fields and the recursive renote shape.
 */
class MisskeyJsonTest {

    private val json = """
        [
          {
            "id": "n1",
            "text": "hello misskey",
            "cw": null,
            "createdAt": "2026-09-08T10:00:00.000Z",
            "userId": "u1",
            "user": {
              "id": "u1",
              "username": "alice",
              "name": "Alice",
              "avatarUrl": "https://misskey.art/av.png",
              "description": "artist"
            },
            "replyId": "n0",
            "renoteId": null,
            "visibility": "public",
            "lang": "en",
            "tags": ["art", "anime"],
            "sensitive": false,
            "unknownFutureField": 123,
            "files": [
              {
                "id": "f1",
                "type": "image/png",
                "url": "https://misskey.art/f1.png",
                "thumbnailUrl": "https://misskey.art/f1.thumb.png",
                "isSensitive": false,
                "comment": "alt",
                "properties": {"width": 1600, "height": 900},
                "blurhash": "ignored"
              }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun parsesNoteWithUserFilesAndTags() {
        val notes = MisskeyJson.parseNotes(json)

        assertEquals(1, notes.size)
        val note = notes[0]
        assertEquals("n1", note.id)
        assertEquals("hello misskey", note.text)
        assertEquals("2026-09-08T10:00:00.000Z", note.createdAt)
        assertEquals("u1", note.userId)
        assertEquals("u1", note.user?.id)
        assertEquals("alice", note.user?.username)
        assertEquals("Alice", note.user?.name)
        assertEquals(listOf("art", "anime"), note.tags)
        assertEquals("public", note.visibility)
        assertEquals(false, note.sensitive)
        assertEquals("n0", note.replyId)
        assertNull(note.renote)
    }

    @Test
    fun parsesFileFacts() {
        val file = MisskeyJson.parseNotes(json)[0].files!![0]
        assertEquals("f1", file.id)
        assertEquals("image/png", file.type)
        assertEquals("https://misskey.art/f1.png", file.url)
        assertEquals("https://misskey.art/f1.thumb.png", file.thumbnailUrl)
        assertEquals(false, file.isSensitive)
        assertEquals("alt", file.comment)
        assertEquals(1600, file.properties?.width)
        assertEquals(900, file.properties?.height)
    }

    @Test
    fun ignoresUnknownPropertiesAndMissingOptionalFields() {
        val minimal = """[{"id":"n2"}]"""
        val note = MisskeyJson.parseNotes(minimal)[0]
        assertEquals("n2", note.id)
        assertNull(note.text)
        assertNull(note.user)
        assertNull(note.renote)
        assertNull(note.tags)
        assertNull(note.files)
        assertNull(note.sensitive)
    }

    @Test
    fun parsesNestedRenoteRecursively() {
        val renoteJson = """
            [
              {
                "id": "wrapper",
                "renoteId": "orig",
                "renote": {
                  "id": "orig",
                  "text": "original",
                  "sensitive": true,
                  "user": {"id": "u9", "username": "bob", "name": "Bob"}
                },
                "user": {"id": "u1", "username": "alice", "name": "Alice"}
              }
            ]
        """.trimIndent()

        val wrapper = MisskeyJson.parseNotes(renoteJson)[0]
        assertEquals("wrapper", wrapper.id)
        assertEquals("orig", wrapper.renoteId)
        val renote = wrapper.renote!!
        assertEquals("orig", renote.id)
        assertEquals("original", renote.text)
        assertTrue(renote.sensitive == true)
        assertEquals("u9", renote.user?.id)
    }
}
