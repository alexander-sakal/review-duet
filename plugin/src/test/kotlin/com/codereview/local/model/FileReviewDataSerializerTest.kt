package com.codereview.local.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class FileReviewDataSerializerTest {

    private val serializer = FileReviewDataSerializer()

    @Test
    fun `round-trips a populated file review`() {
        val original = FileReviewData(
            version = 1,
            mode = "file",
            filePath = "docs/foo-spec.md",
            fileHashAtStart = "abc123",
            lastSeenHash = "abc123",
            createdAt = "2026-05-12T10:00:00Z",
            comments = mutableListOf(
                FileComment(
                    id = 1,
                    line = 42,
                    lineText = "## Authentication flow",
                    contextBefore = listOf("", "Previous section"),
                    contextAfter = listOf("", "We use OAuth..."),
                    text = "this section needs a diagram",
                    status = FileCommentStatus.OPEN,
                    createdAt = "2026-05-12T10:01:00Z"
                )
            )
        )

        val json = serializer.serialize(original)
        val roundTripped = serializer.deserialize(json)

        assertEquals(original, roundTripped)
    }

    @Test
    fun `serializes status as lowercase string`() {
        val data = FileReviewData(
            version = 1, mode = "file", filePath = "x.md",
            fileHashAtStart = "h", lastSeenHash = "h",
            createdAt = "t", comments = mutableListOf(
                FileComment(1, 1, "x", emptyList(), emptyList(), "t", FileCommentStatus.OUTDATED, "t")
            )
        )
        val json = serializer.serialize(data)
        assertTrue(json.contains("\"status\": \"outdated\""), "got: $json")
    }

    @Test
    fun `deserializes all status values`() {
        for (status in FileCommentStatus.entries) {
            val json = """
            { "version": 1, "mode": "file", "filePath": "x.md",
              "fileHashAtStart": "h", "lastSeenHash": "h", "createdAt": "t",
              "comments": [{
                "id": 1, "line": 1, "lineText": "x",
                "contextBefore": [], "contextAfter": [],
                "text": "t", "status": "${status.jsonValue}", "createdAt": "t"
              }] }
            """.trimIndent()
            assertEquals(status, serializer.deserialize(json).comments[0].status)
        }
    }
}
