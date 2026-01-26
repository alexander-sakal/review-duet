package com.codereview.local.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ReviewDataSerializerTest {

    private val serializer = ReviewDataSerializer()

    @Test
    fun `should deserialize valid JSON`() {
        val json = """
        {
          "version": 1,
          "baseCommit": "abc1234",
          "comments": [
            {
              "id": 1,
              "file": "src/Example.php",
              "line": 42,
              "commit": "abc1234",
              "status": "open",
              "resolveCommit": null,
              "thread": [
                {"author": "user", "text": "Fix this", "at": "2024-01-23T10:00:00Z"}
              ]
            }
          ]
        }
        """.trimIndent()

        val data = serializer.deserialize(json)

        assertEquals(1, data.version)
        assertEquals("abc1234", data.baseCommit)
        assertEquals(1, data.comments.size)
        assertEquals(CommentStatus.OPEN, data.comments[0].status)
    }

    @Test
    fun `should serialize to valid JSON`() {
        val data = ReviewData(
            version = 1,
            baseCommit = "abc1234",
            comments = mutableListOf(
                Comment(
                    id = 1,
                    file = "test.kt",
                    line = 10,
                    commit = "abc1234",
                    status = CommentStatus.OPEN,
                    resolveCommit = null,
                    thread = mutableListOf(
                        ThreadEntry("user", "Test", "2024-01-23T10:00:00Z")
                    )
                )
            )
        )

        val json = serializer.serialize(data)

        assertTrue(json.contains("\"status\": \"open\""))
        assertTrue(json.contains("\"baseCommit\": \"abc1234\""))
    }

    @Test
    fun `should round-trip data correctly`() {
        val original = ReviewData(
            version = 1,
            baseCommit = "abc1234",
            comments = mutableListOf()
        )

        val json = serializer.serialize(original)
        val restored = serializer.deserialize(json)

        assertEquals(original.baseCommit, restored.baseCommit)
    }
}
